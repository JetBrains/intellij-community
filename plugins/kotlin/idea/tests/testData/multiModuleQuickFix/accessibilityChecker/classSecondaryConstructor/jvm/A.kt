// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS

interface Some

class A {
    actual val a: Some

    actual <caret>constructor(a: Some, b: Int) {
        this.a = a
    }
}