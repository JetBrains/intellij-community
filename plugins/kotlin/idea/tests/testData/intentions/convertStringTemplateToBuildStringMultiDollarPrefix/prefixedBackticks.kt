// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: LOW

fun test() {
    val `foo bar` = 1
    $$$"$$$`foo bar`"<caret>
}
