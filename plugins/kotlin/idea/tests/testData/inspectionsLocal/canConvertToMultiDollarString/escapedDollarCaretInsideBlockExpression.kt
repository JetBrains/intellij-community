// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "\$  ${15 +<caret> 27}"
}