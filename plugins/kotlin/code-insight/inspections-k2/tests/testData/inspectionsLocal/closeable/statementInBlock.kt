class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    <caret>AC()
}