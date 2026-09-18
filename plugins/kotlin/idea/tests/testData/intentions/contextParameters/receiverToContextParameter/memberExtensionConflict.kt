// SHOULD_FAIL_WITH: Callable reference 's::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// K2_ERROR: EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED
// K2_AFTER_ERROR: EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED

class C {
    fun <caret>String.fn(x: Int): String = x.toString()

    fun usage() {
        val s = "str"
        val ref = s::fn
    }
}
