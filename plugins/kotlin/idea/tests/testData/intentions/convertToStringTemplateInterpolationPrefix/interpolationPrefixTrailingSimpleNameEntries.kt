// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun foo(a: Any) {
    $$"$$a"<caret> + $$"bar$$a" + $$"Bar$$a" + $$"_bar$$a" + $$"0bar$$a" + $$"`bar`$$a" + $$"{bar}$$a" + $$"$" + "."
}