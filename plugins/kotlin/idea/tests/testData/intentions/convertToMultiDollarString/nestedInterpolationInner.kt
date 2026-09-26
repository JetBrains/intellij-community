// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "${"${"${1}${'$'}<caret> ${2}${'$'}"} ${3}${'$'}"} ${4}${'$'}"
}
