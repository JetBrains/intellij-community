class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    <caret>run {
        run {
            AC().also {
                it.read()
            }
        }
    }
}