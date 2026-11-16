// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated: its context argument cannot be used as an explicit receiver
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: UNSUPPORTED_FEATURE
// K2_AFTER_ERROR: UNSUPPORTED_FEATURE

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

context(_: Context)
fun test() {
    print(::fn)
}
