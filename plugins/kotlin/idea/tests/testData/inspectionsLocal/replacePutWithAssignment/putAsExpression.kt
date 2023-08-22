// PROBLEM: none
// WITH_STDLIB

val map = mutableMapOf(42 to "foo")

fun foo() = map.<caret>put(60, "bar")
