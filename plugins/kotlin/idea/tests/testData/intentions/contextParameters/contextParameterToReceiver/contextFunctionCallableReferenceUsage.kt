// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: Unresolved reference 'fn'.
// K2_ERROR: No context argument for '_: Context' found.

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

fun test() {
    ::fn
}
