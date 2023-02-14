// WITH_STDLIB
fun test(): Int = bar { <caret>foo()

fun foo() = 42

fun bar(f: () -> Int) = f()
