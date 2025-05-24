class A {
    val x: Int
        get() = y
        <caret>set(value) {}
    fun f(){}
}
