// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

class A {
    actual class M
    actual fun <caret>a(): M = TODO()
}