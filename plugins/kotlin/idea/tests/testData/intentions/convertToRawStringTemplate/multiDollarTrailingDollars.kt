// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation


fun test(foo: String) {
    $$"foo$bar$$$" +<caret> "baz$" + "_a$" + "{}$" + "`boo`$" + 0 + "."
}