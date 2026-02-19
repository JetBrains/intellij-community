// WITH_STDLIB
// PROBLEM: none

fun foo(arg: Int) = arg.toString()

fun bar(f: () -> (Int) -> String) {}

val someFun = bar { <caret>::foo }