// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "${'$'}100<caret> + ${"$"}200 = ${'$'}300"
}

