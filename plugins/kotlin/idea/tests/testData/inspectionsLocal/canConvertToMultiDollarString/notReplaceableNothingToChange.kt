// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test(some: Int) {
    "$10 10$ $$$$$ ${3 + 2} $some<caret>"
}