// PROBLEM: none
// FIX: none
// WITH_STDLIB
fun testEmptyRange() {
    var start = 0
    var end = start
    end = 1
    for (i in start <caret>until end) {}
}