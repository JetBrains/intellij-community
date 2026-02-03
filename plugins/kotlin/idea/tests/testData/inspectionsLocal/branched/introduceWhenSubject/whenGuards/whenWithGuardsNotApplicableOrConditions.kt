// COMPILER_ARGUMENTS: -Xwhen-guards
// PROBLEM: none

fun test(a: Any) {
    when<caret> {
        a is String && a.isNotEmpty() || a is Int -> Unit
        else -> Unit
    }
}
