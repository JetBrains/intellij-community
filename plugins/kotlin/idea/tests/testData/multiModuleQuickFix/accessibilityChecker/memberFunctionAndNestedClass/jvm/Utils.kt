// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS


class A {
    class M
    actual fun <caret>a(): M = TODO()
}