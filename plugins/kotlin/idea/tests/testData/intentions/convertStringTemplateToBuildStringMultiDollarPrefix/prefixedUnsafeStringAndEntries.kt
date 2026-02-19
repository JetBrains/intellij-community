// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: LOW

fun test() {
    val d = "d"
    val e = "e"
    $$"$$d$a$b$${e}$c"<caret>
}
