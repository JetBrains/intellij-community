// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any, b: Any, c: Any) {
    $$"$${a}$${b}$${c}"<caret>
}
