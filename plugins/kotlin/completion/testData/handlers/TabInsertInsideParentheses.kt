// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(a: Int) {
}

fun test() {
    val vvvvv = 12
    foo(vv<caret>)
}
// AUTOCOMPLETE_SETTING: true