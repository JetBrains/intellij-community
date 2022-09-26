// WITH_STDLIB

fun a(b: Boolean) {
    <caret>throw if (b) RuntimeException() else Exception()
}