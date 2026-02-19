// WITH_STDLIB
// AFTER-WARNING: The expression is unused
private fun St<caret>ring?.f() {
    when (this) {
        null -> ""
        else -> substring(1)
    }
}