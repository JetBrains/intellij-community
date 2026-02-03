open class A(x: Int) {
    fun xprintln(x: String) {}
    fun xprintln() {}
    fun xprintln(x: Boolean) {}

    fun d(x: Int) {
        xprintln(<caret>)
    }
}
