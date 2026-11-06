// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: SYNTAX

fun test() {
    "foo${$$"$${}"}boo"<caret>
}
