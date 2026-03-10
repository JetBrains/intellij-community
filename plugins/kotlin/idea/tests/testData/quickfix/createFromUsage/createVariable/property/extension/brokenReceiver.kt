// "Create extension property 'ERROR.foo'" "false"
// ERROR: 'this' is not defined in this context
// ERROR: Variable expected
// K2_ERROR: 'this' is not defined in this context.
// K2_ERROR: Unresolved reference 'foo'.
// K2_AFTER_ERROR: 'this' is not defined in this context.
// K2_AFTER_ERROR: Unresolved reference 'foo'.

fun main(args: Array<String>) {
    this.f<caret>oo = ""
}