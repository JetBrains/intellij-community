// PROBLEM: none
class AC : AutoCloseable {
    fun getA(): String = "a"
    fun getB(): String = "b"
    override fun close() {
        print("close")
    }
}

fun c(): Pair<String, String> {
    val s = <caret>AC()
    val a = s.getA()
    val b = s.getB()
    s.close()
    return a to b
}
