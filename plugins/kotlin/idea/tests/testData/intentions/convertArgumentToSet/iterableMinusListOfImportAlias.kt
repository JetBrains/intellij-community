// WITH_STDLIB
// IS_APPLICABLE: false

import kotlin.collections.listOf as someFunction

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = someFunction("a", "b", "c", "e")
    val c = a - <caret>b
}
