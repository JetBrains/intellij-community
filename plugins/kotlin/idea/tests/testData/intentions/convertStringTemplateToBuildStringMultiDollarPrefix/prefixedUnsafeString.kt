// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: LOW

fun test() {
    $$"$a$b$c"<caret>
}