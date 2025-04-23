// WITH_STDLIB
// PROBLEM: none

val Any.p: Any get() = with("p") { p<caret> }
val String.p: Any
    get() = this