// WITH_STDLIB
// AFTER-WARNING: Variable 's' is never used
fun test(foo: String, bar: Int, baz: Int) {
    val s = <caret>"${foo.length}, " + // comment1
            // comment2
            "$bar, " + // comment3
            // comment4
            "$baz" // comment5
}
