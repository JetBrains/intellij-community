// IS_APPLICABLE: false
fun <T> test(x: T) {}

fun interface I<T> {
    fun test(x: T)
}

typealias IAlias<T> = I<T>

fun main() {
    val i = <caret>IAlias<String> { x -> test(x) }
}

// IGNORE_K1