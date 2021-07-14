// PROBLEM: none
fun test(d : D) = d.x == null || <caret>d.x < 0

data class D(val x: Int?)
