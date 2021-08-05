// WITH_RUNTIME
fun test(foo: String, bar: Int, baz: Int) {
    val s = <caret>"${foo.length}, " + // comment1
            "$bar, " + // comment2
            "$baz" // comment3
}
