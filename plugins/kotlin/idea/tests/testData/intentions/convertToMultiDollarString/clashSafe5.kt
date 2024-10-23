// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "\$\$42identifier<caret>"
}
