// IS_APPLICABLE: false
// WITH_STDLIB
// PROBLEM: none
import java.io.Closeable

class Resource : Closeable {
    fun doStuff(): Unit = TODO()
    override fun close(): Unit = close(status = 0)
    fun close(status: Int): Unit = TODO()
}

fun test() {
    val resource = Resource()
    <caret>try {
        resource.doStuff()
    } finally {
        resource.close(status = 1)
    }
}