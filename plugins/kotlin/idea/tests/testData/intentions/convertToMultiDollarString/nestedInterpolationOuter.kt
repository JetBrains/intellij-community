// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    "${"${"${1}${'$'} ${2}${'$'}"} ${3}${'$'}"}<caret> ${4}${'$'}"
}
