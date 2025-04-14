// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"$${1 + 2}foo$bar"<caret>
}
