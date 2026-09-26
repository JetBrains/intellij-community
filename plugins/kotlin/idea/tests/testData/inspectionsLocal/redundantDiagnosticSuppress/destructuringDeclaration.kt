fun d() {
    val (a, @Suppress("<caret>UNUSED_VARIABLE") b) = A(1,  2)
    b
}

data class A(val i: Int, val b: Int)
