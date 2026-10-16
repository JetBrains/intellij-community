// COMPILER_ARGUMENTS: -Xwhen-guards
// HIGHLIGHT: INFORMATION
// FIX: Introduce 'when' subject

fun test(a: Any) {
    when<caret> {
        a is String && a.isNotEmpty() -> Unit
        a is Int -> Unit
        else -> Unit
    }
}
