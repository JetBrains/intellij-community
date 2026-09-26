// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun foo(x: Any) {
    $$$"$$$x$a$$${x}"<caret>
}