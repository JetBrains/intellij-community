// IS_APPLICABLE: false
// WITH_STDLIB

fun <T> foo(list: List<T>): Int = 0

fun bar(): Int {
    return foo(listOf<caret><String>())
}