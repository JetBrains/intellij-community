// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NO_CONTEXT_ARGUMENT

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

fun test() {
    ::fn
}
