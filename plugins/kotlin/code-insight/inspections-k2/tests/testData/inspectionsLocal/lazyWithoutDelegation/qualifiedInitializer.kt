class A {
    fun computeLazily(x: Int) = lazy { x.toString() }
}

class B {
    private val p =<caret> A().computeLazily(5)

    fun test() {
        println(p.value)
    }
}
