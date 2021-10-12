// WITH_RUNTIME

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = mutableListOf("a", "b", "c", "e")
    val c = a - <caret>b
}
