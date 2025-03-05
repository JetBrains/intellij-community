// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(a: Any) {
    when<caret> {
        a is String && (a[0] == 'f' || a[0] == 'b') -> Unit
        else -> Unit
    }
}
