// "Implement members" "true"
// DISABLE-ERRORS
// IGNORE_K2

actual interface ExpInterface {
    actual fun first()
}

actual class ExpImpl<caret> : ExpInterface