// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun foo() {
    $$"foo$bar"<caret> + 0 + "."
}