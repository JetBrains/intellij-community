// JS_WITH_STDLIB
// HIGHLIGHT: INFORMATION
// K2_ERROR: Unsupported [dynamic type].
// K2_AFTER_ERROR: Unsupported [dynamic type].
fun foo(a: dynamic, b: String): Boolean {
    return a.<caret>equals(b)
}