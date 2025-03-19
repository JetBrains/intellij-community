// SET_TRUE: ALLOW_TRAILING_COMMA

context(
    a: Int,
    b: Int,
    c: Int,
)
fun test1() {
}

context(
    a: Int,
    b: Int,
    c: Int,
)
fun test2() {
}

context(a: Int, b: Int, c: Int)
fun test3() {
}

context(a: Int, b: Int, c: Int)
fun test4() {
}

context(a: Int)
fun test5() {
}

context(
    a: Int,
)
fun test6() {
}

context(
    a: Int,
    b: Int,
)
fun test7() {
    context(
        c: Int,
        d: Int,
    )
    fun test7() {

    }
}
