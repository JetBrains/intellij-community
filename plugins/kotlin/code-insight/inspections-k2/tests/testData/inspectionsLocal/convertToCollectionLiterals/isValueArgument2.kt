// COMPILER_ARGUMENTS: -Xcollection-literals
fun test(x: MutableSet<Int>, y: String) {
}

fun callTest() {
    test(mutableSetOf<caret>(1, 2, 3), "abc")
}