// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1

fun test(foo: String) {
    foo +<caret> $$"bar$baz\n"
}