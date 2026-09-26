// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NO_CONTEXT_ARGUMENT

interface Context

context(_<caret>: Context)
val prop: Int
    get() = 0

fun test() {
    ::prop
}
