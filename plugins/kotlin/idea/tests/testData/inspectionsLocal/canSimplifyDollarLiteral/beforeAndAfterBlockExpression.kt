// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"$${'$'}\$$\$<caret>$${42}\$Foo"
}
