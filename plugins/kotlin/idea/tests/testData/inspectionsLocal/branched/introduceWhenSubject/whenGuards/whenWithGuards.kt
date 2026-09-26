// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(a: Any) {
    when<caret> {
        a is String && a.isNotEmpty() -> Unit
        a is Int && a > 0 -> Unit
        else -> Unit
    }
}
