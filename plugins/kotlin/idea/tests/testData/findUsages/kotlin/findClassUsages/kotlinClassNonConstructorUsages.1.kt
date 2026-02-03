package client

import server.Server

class Client(name: String = Server.NAME) : Server() {
    var nextServer: Server? = Server()
    val name = Server.NAME

    fun foo(s: Server) {
        val server: Server = s
        println("Server: $server")
    }

    fun getNextServer2(): Server? {
        return nextServer
    }

    override fun work() {
        super<Server>.work()
        println("Client")
    }
}

object ClientObject : Server() {

}

fun Client.bar(s: Server = Server()) {
    foo(s)
}

fun Client.hasNextServer(): Boolean {
    return getNextServer2() != null
}

fun Any.asServer(): Server? {
    return if (this is Server) this as Server else null
}
