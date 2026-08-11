// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
fun test(f: (Int) -> Int) {}

fun refer() {
    val v = test(::<caret>foo)
}
