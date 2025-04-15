// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any, b: Any, c: Any) {
    $$$"a$$${a}$b$$$b$$c$$$c"<caret>
}
