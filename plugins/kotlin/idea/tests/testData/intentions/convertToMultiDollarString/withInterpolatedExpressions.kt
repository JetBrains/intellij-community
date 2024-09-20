// IS_APPLICABLE: true
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    val foo = "foo"
    "${foo} bar<caret> $foo ${1 + 2}"
}
