// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(x: String) {}

fun test() {
    val variable = 1
    foo("", var<caret>)
}

// AUTOCOMPLETE_SETTING: true