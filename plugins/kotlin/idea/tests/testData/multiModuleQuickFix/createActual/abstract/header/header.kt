// "Add missing actual declarations" "true"
// IGNORE_K2

expect abstract class <caret>Abstract {
    fun foo(param: String): Int

    abstract fun String.bar(y: Double): Boolean

    val isGood: Boolean

    abstract var status: Int
}