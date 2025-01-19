// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2-ERROR: Syntax error: Incorrect template argument.
// K2-AFTER-ERROR: Syntax error: Incorrect template argument.

fun test() {
    "foo${}<caret>"
}