// ERROR: 'when' expression must be exhaustive, add necessary 'RW' branch or 'else' branch instead
// AFTER_ERROR: 'when' expression must be exhaustive, add necessary 'RW' branch or 'else' branch instead
// K2_ERROR: 'when' expression must be exhaustive. Add the 'RW' branch or an 'else' branch.
// K2_AFTER_ERROR: 'when' expression must be exhaustive. Add the 'RW' branch or an 'else' branch.
// IS_APPLICABLE: true
enum class AccessMode { READ, WRITE, RW }
fun <T> run(f: () -> T) = f()
fun whenExpr(access: AccessMode) {
    <caret>run {
        println("run")
        when (access) {
            AccessMode.READ -> println("read")
            AccessMode.WRITE -> println("write")
        }
    }
}
fun println(s: String) {}