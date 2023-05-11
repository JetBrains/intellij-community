// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(paramTest: Int = 12) {}

fun test() {
    // '=' is expected
    foo(param<caret>)
}

// AUTOCOMPLETE_SETTING: true