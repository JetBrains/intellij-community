// "Create expected class in common module testModule_Common" "true"
// DISABLE-ERRORS
// IGNORE_K2

class A<T> {
    actual inner class <caret>B<F : T>
}