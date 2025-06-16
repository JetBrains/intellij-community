// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS

actual class A<T> {
    actual fun <caret>a(): T = TODO()
}