// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: NORMAL

fun test() {
    val d = "d"
    val e = "e"
    $$"$$d$a$b$${e}$c"<caret>
}
