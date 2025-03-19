// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: Syntax error: Incorrect template argument.

fun test() {
    "foo${$$"$${}"}boo"<caret>
}
