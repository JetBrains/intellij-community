package client

import server.Server

class Client(name: String = Server.NAME) : Server {

    constructor(ctrParam: Server, name: String) : this(name)

    lateinit var nextServer: Server
    val name = Server.NAME
    val name2 = Server.Companion.NAME
    val name3 = Server.Inner()
    val clazz = Server::class.java

    fun foo(s: Server) {
        val server: Server = s
        println("Server: $server")
    }

    fun getNextServer2(): Server? {
        return nextServer
    }

    fun withFunctionalTypeInParam(p: (s: Server) -> Unit) {}
    fun withFunctionalTypeInParam2(p: () -> Server) {}
    fun withReturningFunctionalType() : (s: Server) -> Unit = {}
    fun withReturningFunctionalType2() : () -> Server = { object : Server {} }

    fun withAnonymousFunction() {
        withFunctionalTypeInParam(fun(s: Server) {})
    }
    fun withAnonymousFunction2() {
        withFunctionalTypeInParam2(fun(): Server = object : Server {})
    }

    override fun work() {
        super<Server>.work()
        println("Client")
    }

    companion object : Server {

    }
}

object ClientObject : Server {
    fun useInnerObject() {
        val c = Server.InnerObject
    }
}

abstract class Servers : Iterator<Server> {

}

fun Iterator<Server>.f(p: Iterator<Server>): Iterator<Server> = this

fun Client.bar(s: Server = Client()) {
    foo(s)
}

fun Client.hasNextServer(): Boolean {
    return getNextServer2() != null
}

fun Any.asServer(): Server? {
    when (this) {
        is Server -> println("Server!")
    }
    return if (this is Server) this as Server else this as? Server
}