// IS_APPLICABLE: true
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "${'$'}100<caret> + ${"$"}200 = ${'$'}300"
}

