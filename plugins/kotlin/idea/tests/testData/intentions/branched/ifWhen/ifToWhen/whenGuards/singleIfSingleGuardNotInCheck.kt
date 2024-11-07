// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(i: Int, b: Boolean) {
    if<caret> (i !in 0..1 && b) {
    } else {}
}