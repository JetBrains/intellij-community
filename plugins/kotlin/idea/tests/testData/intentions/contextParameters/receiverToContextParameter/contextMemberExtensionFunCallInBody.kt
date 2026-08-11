// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
/* KTIJ-34522 */

context(c1: Int)
fun <caret>Bar.foo() {
    "foo".bar()
}

class Bar {
    context(c: Int)
    fun String.bar() {}
}
