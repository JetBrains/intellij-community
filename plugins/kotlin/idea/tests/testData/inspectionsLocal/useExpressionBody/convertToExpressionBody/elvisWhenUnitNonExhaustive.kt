// K2_ERROR: 'when' expression must be exhaustive. Add the 'RW' branch or an 'else' branch.
// ERROR: 'when' expression must be exhaustive, add necessary 'RW' branch or 'else' branch instead
// PROBLEM: none

enum class AccessMode { READ, WRITE, RW }
fun whenExpr(access: AccessMode) {
    <caret>println("result") ?: when (access) {
        AccessMode.READ -> println("read")
        AccessMode.WRITE -> println("write")
    }
}
fun println(s: String) {}