// PROBLEM: none
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xcontext-receivers
// K2_ERROR: CONTEXT_RECEIVERS_DEPRECATED
// K2_ERROR: UNRESOLVED_LABEL

context(String<caret>)
fun stringFromContext(): String {
    return this@String
}