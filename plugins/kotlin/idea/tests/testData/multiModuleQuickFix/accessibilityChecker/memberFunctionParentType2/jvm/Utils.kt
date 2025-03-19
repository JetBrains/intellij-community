// "Create expected class in common module testModule_Common" "true"
// ERROR: Unresolved reference: T
// ERROR: Unresolved reference: TODO
// IGNORE_K2
actual class A<T> {
    class B {
        actual fun <caret>a(): T = TODO()
    }
}