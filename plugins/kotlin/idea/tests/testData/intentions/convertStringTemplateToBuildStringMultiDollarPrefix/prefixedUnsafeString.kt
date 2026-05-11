// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: NORMAL

fun test() {
    $$"$a$b$c"<caret>
}