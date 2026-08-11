// PROBLEM: none
// WITH_STDLIB
val <T> <caret>T.bar: T?
    get() = null

fun test() {
    "".bar
}