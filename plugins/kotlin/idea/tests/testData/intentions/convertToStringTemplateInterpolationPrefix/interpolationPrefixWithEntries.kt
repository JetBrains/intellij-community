// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun foo(a: Any, b: Any, c: Any) {
    $$"foo$bar $$a"<caret> + 0 + $$"$${b}" + 0.0 + $$$" baz$$boo $$$c"
}