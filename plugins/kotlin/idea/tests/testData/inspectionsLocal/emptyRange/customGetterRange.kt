// PROBLEM: none
// FIX: none
// WITH_STDLIB
var x = 0
val end: Int
    get() = x

fun testEmptyRange() {
    val start = 0
    x = 1
    for (i in start <caret>until end) {}
}