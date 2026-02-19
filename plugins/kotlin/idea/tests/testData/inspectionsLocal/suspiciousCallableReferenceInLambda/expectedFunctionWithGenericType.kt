// WITH_STDLIB
// PROBLEM: none

fun foo(arg: Int) = arg.toString()

fun <T : (Int) -> String> bar(f: () -> T) {}

val someFun = bar { ::foo }<caret>