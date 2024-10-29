// IS_APPLICABLE: true
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    val name: String = "Foo"
    val suffix: String = "Bar"
    val fullName = "`${'$'}${'$'}Baz${'$'}Boo${'$'}$name$suffix`<caret>"
}
