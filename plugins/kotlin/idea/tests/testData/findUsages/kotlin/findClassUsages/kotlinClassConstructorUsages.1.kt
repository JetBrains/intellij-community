package client

import server.Server

class Client : Server("") {
    var nextServer: Server? = Server("")
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

object ClientObject : Server("") {

}

class Client2 : Server {
    constructor(name: String) : super(name) {

    }

    constructor() : super("") {

    }
}

fun Client.bar(s: Server) {
    foo(s)
}

fun Client.hasNextServer(): Boolean {
    return getNextServer2() != null
}

fun Any.asServer(): Server? {
    return if (this is Server) this as Server else null
}
