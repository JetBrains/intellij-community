// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1

fun test(foo: String) {
    $$"$$foo" +<caret> $$"bar$$foo" + $$"Bar$$foo" + $$"_bar$$foo" + $$"0bar$$foo" + $$"`bar`$$foo" + $$"{bar}$$foo" + $$"$" + "."
}