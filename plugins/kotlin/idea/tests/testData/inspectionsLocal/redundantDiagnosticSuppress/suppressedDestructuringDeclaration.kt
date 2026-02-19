// PROBLEM: none

fun d() {
    val (a, @Suppress("<caret>UNUSED_VARIABLE") b) = A(1, 2)
}

data class A(val i: Int, val b: Int)