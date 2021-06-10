// WITH_RUNTIME
fun test(foo: String, bar: Int, baz: Int) {
    val s = <caret>"foo = $foo" +
            """foo = $foo""" +
            "$bar" +
            "${baz}" +
            """$bar""" +
            """${baz + baz}""" +
            foo.length +
            "bar + baz = ${bar + baz}"
}
