// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers

context(String<caret>)
fun stringFromContext(): String {
    return this@String
}