// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(a: Any) {
    when<caret> {
        a is String && a.isNotEmpty() && a[0] == '0' && a[1] == '1' && a[2] == '2' -> Unit
        else -> Unit
    }
}
