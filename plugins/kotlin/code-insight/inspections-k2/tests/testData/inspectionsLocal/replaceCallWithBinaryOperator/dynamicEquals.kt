// JS_WITH_STDLIB
// K2_ERROR: Dynamic type is only supported in Kotlin JS.
// K2_AFTER_ERROR: Dynamic type is only supported in Kotlin JS.
// PROBLEM: none
fun foo(a: dynamic, b: String): Boolean {
    return a.<caret>equals(b)
}