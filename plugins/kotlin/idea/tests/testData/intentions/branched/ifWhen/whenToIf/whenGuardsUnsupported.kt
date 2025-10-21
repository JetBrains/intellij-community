// COMPILER_ARGUMENTS: -XXLanguage:-WhenGuards
// IS_APPLICABLE: false
// ERROR: The feature "when guards" is disabled
// K2_ERROR: The feature "when guards" is disabled

private fun test(s: Any) {
    when (s) {
        is Int <caret>if s > 5 -> Unit
        else -> Unit
    }
}
