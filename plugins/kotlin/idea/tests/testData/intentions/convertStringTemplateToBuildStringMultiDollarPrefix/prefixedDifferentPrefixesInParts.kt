// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: LOW

fun test(a: Int, b: Int, c: Int, d: Int, e: Int) {
    $$$$$"a$$$$${a}$b$$$$$b$$c$$$$${c}$$$d$$$$$d$$$$e$$$$${e}"<caret>
}
