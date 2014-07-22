package tracker

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io._
import akka.util.ByteString
import java.net._

trait UdpServer extends Logging {
  implicit val system = ActorSystem("ServerSystem")
  val ref    = system.actorOf(Props(new UdpServerActor(this, port)),
                              name = "udp")
  val port : Int
  def packet(data : ByteString, sender : InetSocketAddress) : Unit

  def send(data : Array[Byte], to : InetSocketAddress) = {
    ref ! Udp.Send(ByteString(new String(data)), to)
  }

  def stop = {
    info("stopping udpserver")
    ref ! Udp.Unbind
  }
}

class UdpServerActor(daddy : UdpServer, port : Integer) extends Actor {
  import context.system
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress("0.0.0.0", 6881))
  def receive = {
    case Udp.Bound(local) =>
      println(sender)
      println(local)
      context.become(ready(sender))
    case Udp.Unbound =>
      //context.stop(self)
      context.system.shutdown()
    case other =>
      println(other)
  }

  def ready(ref : ActorRef) : Receive = {
    case Udp.Received(data, sender) => daddy.packet(data, sender)
    //val processed = ???
    //ref ! Udp.Send(data, sender)
    case Udp.Send(data, to, _) => ref ! Udp.Send(data, to)
    case Udp.Unbind  => ref ! Udp.Unbind
      context.unbecome
  }
}

class DhtTracker(val port : Int) extends UdpServer {
  // val logger = Logger(LoggerFactory.getLogger("name"))
  //  logger.debug("foo")

  def packet(data : ByteString, sender : InetSocketAddress) = {
    try {
      var dec = Bencoding.decode(data.iterator.buffered)
      DHTMessage.message(dec).foreach( resp => {
        val resp2 = Bencoding.encode(resp)
        super.send(resp2, sender)
      })
    } catch {
      case e : Bencoding.DecodeException => {
        info("invalid incoming packet")
      }
      case e : Exception => {
        FileUtils.writeFile("/home/bernie/pkg")(p => {
          p.write(new String(data.toArray, "ISO-8859-1"))
        })
        println("error" + e.getStackTrace)
      }
      case e : Error => {
      }
    }
  }
}