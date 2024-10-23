// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    val a = "A"
    $$"$${'$'}\$$\$<caret>$$a\$Foo"
}
