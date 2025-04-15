// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PRIORITY: LOW

fun test() {
    val a = "a"
    val b = "b"
    val c = "c"
    $$"$$a$$b$$c"<caret>
}