// COMPILER_ARGUMENTS: -Xwhen-guards
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: Introduce 'when' subject

fun test(a: Any) {
    when<caret> {
        a == (true && false) -> Unit
        a is Int -> Unit
        else -> Unit
    }
}
