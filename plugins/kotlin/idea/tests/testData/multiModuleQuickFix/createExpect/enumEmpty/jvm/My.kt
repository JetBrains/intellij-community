// "Create expected enum class in common module testModule_Common" "true"
// DISABLE-ERRORS
// IGNORE_K2

actual enum class <caret>My {
    ;

    actual fun getHello() = "Hello"
}