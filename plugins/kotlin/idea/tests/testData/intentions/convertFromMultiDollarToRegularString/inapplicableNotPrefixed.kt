// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IS_APPLICABLE: false

fun test(a: Int, b: Int) {
    "$${a + b}"<caret>
}