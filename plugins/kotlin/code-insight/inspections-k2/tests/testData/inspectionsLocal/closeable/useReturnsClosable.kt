class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    AC().<caret>use { it.let { AC() } }
}
