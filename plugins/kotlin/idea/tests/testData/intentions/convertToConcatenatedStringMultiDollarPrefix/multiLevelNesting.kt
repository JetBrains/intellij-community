// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any, b: Any, c: Any, d: Any, e: Any) {
    $$"a: $${"$a"}, $${"b: ${b}, c: ${$$$"$$$c"}, d: ${$$"$${d}, e: $${"$e"}"}"}"<caret>
}
