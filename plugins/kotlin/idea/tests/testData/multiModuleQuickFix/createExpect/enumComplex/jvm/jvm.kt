// "Create expected enum class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual enum class <caret>Complex {
    FIRST {
        override fun foo() {}
    };

    actual abstract fun foo()
}