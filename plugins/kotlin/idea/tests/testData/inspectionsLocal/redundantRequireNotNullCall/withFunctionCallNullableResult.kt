// WITH_STDLIB
// PROBLEM: none

fun fn(f: () -> Int?) {
    val x: Int = <caret>requireNotNull(f())
}