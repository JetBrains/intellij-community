// AFTER-WARNING: Parameter 'x' is never used
object A
operator fun A.contains(x: Int): Boolean {
    return true
}

fun test() {
    A.cont<caret>ains(1)
}