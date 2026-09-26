// FIR_IDENTICAL
// FIR_COMPARISON
package TestData

class TestSample() {
    fun test(<caret>) {
    }
}

// EXIST: vararg
// EXIST: noinline
// EXIST: crossinline
// EXIST: context
// NOTHING_ELSE
