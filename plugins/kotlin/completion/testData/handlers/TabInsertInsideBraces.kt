// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(f: (Int) -> Unit) = 12

fun test() {
    val vvvvv = 12
    foo {vv<caret>}
}
// AUTOCOMPLETE_SETTING: true