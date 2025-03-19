// COMPILER_ARGUMENTS: -XXLanguage:-WhenGuards
// IS_APPLICABLE: false
// ERROR: The feature "when guards" is only available since language version 2.2
// K2_ERROR: The feature "when guards" is only available since language version 2.2

private fun test(s: Any) {
    when (s) {
        is Int <caret>if s > 5 -> Unit
        else -> Unit
    }
}
