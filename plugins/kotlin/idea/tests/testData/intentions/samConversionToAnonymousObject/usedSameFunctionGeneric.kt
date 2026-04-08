// IS_APPLICABLE: false
fun <T> test(x: T) {}

fun interface I<T> {
    fun test(x: T)
}

fun main() {
    val i = <caret>I<String> { x -> test(x) }
}

// IGNORE_K1