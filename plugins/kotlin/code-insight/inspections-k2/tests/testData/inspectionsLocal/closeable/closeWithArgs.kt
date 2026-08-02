class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
    fun close(times: Int) {
        repeat(times) { print("close") }
    }
}

fun c() {
    val s = <caret>AC()
    s.read()
    s.close(2)
}
