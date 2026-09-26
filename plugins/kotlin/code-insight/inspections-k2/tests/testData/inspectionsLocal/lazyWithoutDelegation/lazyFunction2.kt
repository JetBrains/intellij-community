// PROBLEM: none
class A {
    fun foo(x: Int) {}
    fun computelazily(x: Int) = lazy { foo(x) }

    private val p =<caret> computelazily(5)

    fun test() {
        if (p.isInitialized()) {
            println(p.value)
        }
    }
}