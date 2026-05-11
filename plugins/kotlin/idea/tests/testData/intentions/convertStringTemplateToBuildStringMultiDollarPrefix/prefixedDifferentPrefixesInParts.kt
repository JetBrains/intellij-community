// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: NORMAL

fun test(a: Int, b: Int, c: Int, d: Int, e: Int) {
    $$$$$"a$$$$${a}$b$$$$$b$$c$$$$${c}$$$d$$$$$d$$$$e$$$$${e}"<caret>
}
