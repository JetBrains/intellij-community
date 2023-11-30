// "Remove 'actual' modifier" "true"
// IGNORE_K2

actual interface ExpInterface {
    actual fun first()
}

actual class ExpImpl : ExpInterface {
    actual override fun <caret>first() { }
}