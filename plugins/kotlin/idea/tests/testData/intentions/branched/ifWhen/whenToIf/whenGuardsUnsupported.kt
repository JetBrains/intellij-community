// COMPILER_ARGUMENTS: -XXLanguage:-WhenGuards
// IS_APPLICABLE: false
// ERROR: The feature "when guards" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xwhen-guards', but note that no stability guarantees are provided.

private fun test(s: Any) {
    when (s) {
        is Int <caret>if s > 5 -> Unit
        else -> Unit
    }
}
