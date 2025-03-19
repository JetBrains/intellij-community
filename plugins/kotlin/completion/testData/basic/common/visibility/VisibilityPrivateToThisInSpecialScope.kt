// FIR_IDENTICAL
// FIR_COMPARISON
class A<in I> {
    private val bar: I

    private fun baz(): I = null!!


    fun test() {
        with(A()) {
            ba<caret>
        }
    }
}

// INVOCATION_COUNT: 1
// EXIST: bar, baz
