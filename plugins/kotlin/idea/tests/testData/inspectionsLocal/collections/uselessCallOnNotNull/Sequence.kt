// WITH_STDLIB

fun test(s: Sequence<Int>) {
    val foo = s<caret>.orEmpty()
}