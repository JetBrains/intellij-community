// "Create expected function in common module testModule_Common" "false"
// DISABLE_ERRORS

actual fun foo(): Boolean = false
internal actual fun <caret>foo(i: Int, d: Double, s: String) = s == "$i$d"
// IGNORE_K1