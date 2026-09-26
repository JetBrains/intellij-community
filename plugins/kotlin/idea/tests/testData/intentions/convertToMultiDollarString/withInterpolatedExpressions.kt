// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    val foo = "foo"
    "${foo} bar<caret> $foo ${1 + 2}"
}
