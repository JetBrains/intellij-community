// "Create expected class in common module testModule_Common" "true"
// ERROR: Unresolved reference: T
// ERROR: Unresolved reference: TODO

actual class A<T> {
    class B {
        actual fun <caret>a(): T = TODO()
    }
}