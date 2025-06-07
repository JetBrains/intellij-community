// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(a: Any) {
    if<caret> (a is Int && a > 0) {
    } else {}
}