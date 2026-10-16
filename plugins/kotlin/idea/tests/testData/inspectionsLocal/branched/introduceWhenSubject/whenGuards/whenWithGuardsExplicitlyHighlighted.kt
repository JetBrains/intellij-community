// COMPILER_ARGUMENTS: -Xwhen-guards
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: Introduce 'when' subject

fun test(a: Any) {
    when<caret> {
        a is String && a.isNotEmpty() -> Unit
        a is Int -> Unit
        else -> Unit
    }
}
