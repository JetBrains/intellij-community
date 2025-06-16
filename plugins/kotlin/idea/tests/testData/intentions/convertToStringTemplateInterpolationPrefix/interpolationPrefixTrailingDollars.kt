// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun foo() {
    $$"foo$bar$$$"<caret> + "baz$" + "_a$" + "{}$" + "`boo`$" + 0 + "."
}