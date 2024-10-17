// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1

fun test() {
    val a = "A"
    $$"$${'$'}\$$\$<caret>$$a\$Foo"
}
