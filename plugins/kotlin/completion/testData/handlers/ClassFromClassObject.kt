// FIR_COMPARISON
// FIR_IDENTICAL
class Test {
    companion object {
        class Some
    }
}

fun test() {
    Test.Companion.S<caret>
}
// AUTOCOMPLETE_SETTING: true