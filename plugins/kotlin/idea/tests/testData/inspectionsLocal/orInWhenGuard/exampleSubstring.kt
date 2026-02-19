// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(a: Any) {
    when (a) {
        is String if a[0] == 'f' || a[0]<caret> == 'b' -> Unit // suggest clarifying braces for the guard
        else -> Unit
    }
}
