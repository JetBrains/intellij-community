// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
/* KTIJ-34522 */

fun <caret>Bar.foo() {
    "foo".bar()
}

class Bar {
    fun String.bar() {}
}
