// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
//TODO: KTIJ-33598
// K2_ERROR:

fun test() {
    "foo${$$"$${}"}boo"<caret>
}
