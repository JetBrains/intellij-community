// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1

fun test() {
    $$"$${'$'}\$$\$<caret>\t\$Foo"
}
