// FIR_COMPARISON
// FIR_IDENTICAL
package a

class Test {
    companion object {
        class Some
    }
}

fun test() {
    a.Test.Companion.S<caret>
}
// AUTOCOMPLETE_SETTING: true