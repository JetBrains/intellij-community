
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"\\\n\"\'\n\u0041"<caret>
}