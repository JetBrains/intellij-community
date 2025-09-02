// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any, b: Any, c: Any, d: Any) {
    $$"$${"${$$$"$$$a $$$b"} $c"} $$d: foo$bar"<caret>
}
