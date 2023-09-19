// FIR_IDENTICAL
// FIR_COMPARISON
package testdata.kotlin.data

class TestSample() {
    fun main(args : Array<String>) {
        testdata.kot<caret>lin.data.TestSample()
    }
}

// EXIST: kotlin
// EXIST_K2: kotlin.