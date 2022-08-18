// AFTER-WARNING: The expression is unused
fun foo() {
    when (1) {
        else -> {
            foo()<caret>
        }
    }
}