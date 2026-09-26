// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: NORMAL

fun test() {
    val `foo bar` = 1
    $$$"$$$`foo bar`"<caret>
}
