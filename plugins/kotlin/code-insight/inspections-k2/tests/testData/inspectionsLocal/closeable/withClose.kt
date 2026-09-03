class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    val s = <caret>AC()
    s.read()
    s.close()
}
