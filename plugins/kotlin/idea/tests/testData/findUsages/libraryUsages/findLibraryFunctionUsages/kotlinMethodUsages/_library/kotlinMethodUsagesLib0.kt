package server

public open class Server() {
    open fun processRequest() = "foo"
}

public class ServerEx() : Server() {
    override fun processRequest() = "foofoo"
}


