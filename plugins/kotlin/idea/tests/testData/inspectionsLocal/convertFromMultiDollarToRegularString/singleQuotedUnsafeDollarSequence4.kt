// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"foo${1 + 2}"<caret>
}