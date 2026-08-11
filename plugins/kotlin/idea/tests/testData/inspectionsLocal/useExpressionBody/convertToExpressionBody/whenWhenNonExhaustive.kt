// ERROR: 'when' expression must be exhaustive, add necessary 'false' branch or 'else' branch instead
// PROBLEM: none
// K2_ERROR: NO_ELSE_IN_WHEN

enum class AccessMode { READ, WRITE, RW }
fun whenExpr(mode: Boolean, access: AccessMode) {
    <caret>when (access) {
        AccessMode.READ -> when (mode) {
            true -> println("read")
            false -> println("noread")
        }
        AccessMode.WRITE -> when (mode) {
            true -> println("write")
        }
        AccessMode.RW -> when (mode) {
            true -> println("both")
            else -> println("no both")
        }
    }
}
fun println(s: String) {}