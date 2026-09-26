// WITH_STDLIB
// AFTER-WARNING: Variable 's' is never used
fun test(foo: String, bar: Int, baz: Int) {
    val s = "foo = $foo" +
            """foo = $foo""" +
            "$bar" +
            "${baz}" +<caret>
            """$bar""" +
            """${baz + baz}""" +
            foo.length +
            "bar + baz = ${bar + baz}"
}
