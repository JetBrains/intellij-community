// WITH_RUNTIME
// IS_APPLICABLE: false

fun <T : CharSequence> foo(a: Iterable<T>) {
    val c = a - <caret>kotlin.collections.listOf("a", "b", "c", "e")
}
