// "Create extension property 'ERROR.foo'" "false"
// K2_ERROR: [NO_THIS] 'this' is not defined in this context.
// ERROR: 'this' is not defined in this context
// ERROR: Variable expected

fun main(args: Array<String>) {
    this.f<caret>oo = ""
}