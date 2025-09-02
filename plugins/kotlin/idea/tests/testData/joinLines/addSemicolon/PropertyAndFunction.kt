class A {
    val y = 1
    var x: Int
        get() = y
        <caret>set(value) {}
    fun f(){}
}
