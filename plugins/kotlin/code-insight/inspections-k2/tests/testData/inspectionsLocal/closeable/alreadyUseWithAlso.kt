// PROBLEM: none
class AC : AutoCloseable {
    fun read() {
        print("read")
    }
    override fun close() {
        print("close")
    }
}

fun c() {
    <caret>AC().also {
        it.read()
    }.use { it.read() }
}