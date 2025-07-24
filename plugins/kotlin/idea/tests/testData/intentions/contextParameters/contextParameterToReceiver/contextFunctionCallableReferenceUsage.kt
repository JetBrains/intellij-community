// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Callable reference to 'context(_: Context) fun fn(): Int' is unsupported because it has context parameters.
// K2_AFTER_ERROR: Unresolved reference 'fn'.

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

fun test() {
    ::fn
}
