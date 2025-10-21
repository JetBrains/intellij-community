class A {
    operator fun get(x: String) = 1
    operator fun get(x: Int, y: Boolean) = 2

    fun d(x: Int) {
        this[<caret>1, false]
    }
}