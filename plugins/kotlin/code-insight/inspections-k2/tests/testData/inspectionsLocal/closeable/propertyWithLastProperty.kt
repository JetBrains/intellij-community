class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    fun getResult(): String = "result"
    override fun close() {
        print("close")
    }
}

fun c() {
    val s = <caret>AC()
    s.read()
    val result = s.getResult()
}
