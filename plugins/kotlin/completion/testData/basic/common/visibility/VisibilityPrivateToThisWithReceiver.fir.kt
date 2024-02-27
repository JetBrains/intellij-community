// FIR_COMPARISON
//  Diff reason: KT-55446

class A<in I> {
    private val bar: I

    private fun foo(): I = null!!


    fun test(a: A<Int>) {
        a.<caret>
    }
}

// INVOCATION_COUNT: 1
// EXIST: bar, foo
