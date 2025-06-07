// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(i: Int, b: Boolean, b2: Boolean) {
    if<caret> (i in 0..1 && b || b2) {
    } else {}
}
