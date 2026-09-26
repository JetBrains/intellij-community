// FULL_JDK

import java.io.Serializable

object Foo : Serializable {
    private const val serialVersionUID: Long = 0L
}

object Bar : Serializable {
    @JvmStatic
    private val serialVersionUID: Long = 0L
}

fun main() {
    Foo
    Bar
}