open class A(x: Int) {
    fun m(x: Int, y: Int) = 2

    fun d(x: Int) {
        m(1, 2, 3<caret>)
    }
}
