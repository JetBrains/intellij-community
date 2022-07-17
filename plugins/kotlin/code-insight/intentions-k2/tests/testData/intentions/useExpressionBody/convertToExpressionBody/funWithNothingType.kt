// WITH_RUNTIME
// IS_APPLICABLE: false

fun foo(): Nothing {
    <caret>throw UnsupportedOperationException()
}
