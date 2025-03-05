// "Implement members" "true"
// DISABLE_ERRORS

actual interface ExpInterface {
    actual fun first()
}

actual class ExpImpl<caret> : ExpInterface