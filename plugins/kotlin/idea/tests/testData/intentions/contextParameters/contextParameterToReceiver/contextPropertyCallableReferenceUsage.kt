// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Callable reference to 'context(_: Context) val prop: Int' is unsupported because it has context parameters.
// K2_AFTER_ERROR: Unresolved reference 'prop'.

interface Context

context(_<caret>: Context)
val prop: Int
    get() = 0

fun test() {
    ::prop
}
