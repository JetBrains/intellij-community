// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// ERROR: An argument is already passed for this parameter
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Type mismatch: inferred type is Base but String was expected
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT

open class Base
class Derived : Base()

context(x: Derived)
fun foo(a: String): String = a

fun main() {
    // Base is NOT a subtype of Derived, so the intention should NOT be applicable
    foo(<caret>Base(), a = "World")
}
