// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any) {
    "${"$"}${"$"}${"$"}${"$"}a: ${a}"<caret>
}
