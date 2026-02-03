// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any) {
    $$"start$${"$a ${"deep nesting ${a}"}baz${42 + 1}"}foo$bar$${$$$"bee$$goo"} end"<caret>
}
