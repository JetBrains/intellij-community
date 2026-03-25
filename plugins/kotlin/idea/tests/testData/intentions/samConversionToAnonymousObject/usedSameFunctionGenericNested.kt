// IS_APPLICABLE: false
fun <T> test(xs: List<T>) {}

fun interface I<T> {
    fun test(xs: List<T>)
}

fun main() {
    val i = <caret>I<String> { xs -> test(xs) }
}

// IGNORE_K1