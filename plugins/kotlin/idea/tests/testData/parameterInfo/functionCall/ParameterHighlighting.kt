open class A(x: Int) {
    fun m(a: String = "a", b: String = "b", c: String = "c", d: String = "d") = 1

    fun d(x: Int) {
        m("a", "b", <caret>"c", "d")
    }
}
/*
Text: (a: String = "a",
b: String = "b",
<highlight>c: String = "c"</highlight>,
d: String = "d"), Disabled: false, Strikeout: false, Green: true
*/
