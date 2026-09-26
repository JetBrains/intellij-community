// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: NORMAL

fun test() {
    val a = "a"
    val b = "b"
    val c = "c"
    $$"$$a$$b$$c"<caret>
}