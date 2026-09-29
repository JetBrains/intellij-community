// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated: its context argument cannot be used as an explicit receiver
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: CALLABLE_REFERENCE_TO_CONTEXTUAL_DECLARATION
// K2_AFTER_ERROR: CALLABLE_REFERENCE_TO_CONTEXTUAL_DECLARATION

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

context(_: Context)
fun test() {
    print(::fn)
}
