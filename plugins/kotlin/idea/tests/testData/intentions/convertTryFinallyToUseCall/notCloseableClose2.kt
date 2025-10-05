// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB
import java.io.Closeable

class Resource : Closeable {
    fun doStuff(): Unit = println()
    @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
    override fun close(): Unit = close(1)
    fun close(status: Int = 0): Unit = println(status)
}

fun main() {
    val resource = Resource()
    <caret>try {
        resource.doStuff()
    } finally {
        resource.close()
    }
}
