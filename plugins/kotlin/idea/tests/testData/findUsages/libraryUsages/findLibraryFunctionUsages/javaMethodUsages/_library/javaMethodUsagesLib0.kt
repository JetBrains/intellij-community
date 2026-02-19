package testing

public open class Server() {
    public open fun processRequest() = "foo"
}

public class ServerEx() : Server() {
    public override fun processRequest() = "foofoo"
}

