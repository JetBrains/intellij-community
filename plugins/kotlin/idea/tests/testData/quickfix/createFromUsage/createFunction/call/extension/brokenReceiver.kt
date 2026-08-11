// "Create extension function 'ERROR.foo'" "false"
// ERROR: 'this' is not defined in this context
// K2_AFTER_ERROR: NO_THIS
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NO_THIS
// K2_ERROR: UNRESOLVED_REFERENCE

fun main(args: Array<String>) {
    this.f<caret>oo()
}