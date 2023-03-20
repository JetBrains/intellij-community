// WITH_STDLIB
// AFTER-WARNING: Variable 's' is never used
fun test(foo: String, bar: Int, baz: Int) {
    val s = <caret>"${foo.length}, " + // comment1
            // comment2
            /* comment3 */
            "$bar, " + // comment4
            // comment5
            // comment6
            "$baz" // comment7
    /* This is a test comment:
        - This is a test bullet point.
        - This is another test bullet point.
    */
}
