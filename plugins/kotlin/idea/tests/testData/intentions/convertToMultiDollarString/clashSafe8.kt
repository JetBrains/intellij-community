// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: Unresolved reference 'foo'.
// K2_AFTER_ERROR: Unresolved reference 'foo'.

fun test() {
    "${'$'}${'$'}${'$'}${'$'}${'$'}${'$'}$foo<caret>"
}
