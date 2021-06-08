// WITH_RUNTIME
fun test(foo: String, bar: Int, baz: Int) {
    val s = <caret>"foo=$foo," +
            "bar=$bar," +
            "baz=${baz + baz}"
}
