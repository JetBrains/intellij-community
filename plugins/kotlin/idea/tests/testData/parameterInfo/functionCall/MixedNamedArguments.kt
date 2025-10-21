open class A(x: Int) {
    fun m(x: Boolean, y: Int) = 1
    fun m(x: Boolean, y: Int, z: Int) = 2

    fun d(x: Int) {
        m(false, y = 23<caret>)
    }
}
