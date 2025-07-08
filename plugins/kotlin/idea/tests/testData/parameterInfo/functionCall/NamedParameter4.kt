open class A(x: Int) {
    fun m(a: String = "a", b: String = "b", c: String = "c", d: String = "d") = 1

    fun d(x: Int) {
        m(b = "x", d = "x", <caret>)
    }
}
