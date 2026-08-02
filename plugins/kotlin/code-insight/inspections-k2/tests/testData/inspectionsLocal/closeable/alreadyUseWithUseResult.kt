class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    val ac = AC().<caret>use { it.read(); AC() }
    ac.read()
}