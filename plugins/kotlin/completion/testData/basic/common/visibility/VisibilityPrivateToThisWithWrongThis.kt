// FIR_IDENTICAL
// FIR_COMPARISON

class A<in I> {
    private val bar: I

    private fun foo(): I = null!!


    fun test() {
        with(A<Int>()) {
            this.<caret>
        }
    }
}

// INVOCATION_COUNT: 1
// ABSENT: bar, foo
