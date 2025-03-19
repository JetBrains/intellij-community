// "Add missing actual members" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual class <caret>My {
    actual fun foo(param: String) = 42

    actual val correct = true
}
