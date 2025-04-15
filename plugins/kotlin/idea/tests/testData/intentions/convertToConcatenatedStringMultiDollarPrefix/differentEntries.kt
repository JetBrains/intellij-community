// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any, b: Any, c: Any) {
    $$"foo$$a bar$baz$$b \n\t$${c}"<caret>
}
