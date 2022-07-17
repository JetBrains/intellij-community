// "Replace with 'str.isEmpty()'" "true"
// WITH_STDLIB

import Bar.bar

fun foo(s: String) {
    <caret>bar(s)
}

object Bar {
    @Deprecated(message = "", replaceWith = ReplaceWith("str.isEmpty()"))
    fun bar(str: String): Boolean = str.isEmpty()
}