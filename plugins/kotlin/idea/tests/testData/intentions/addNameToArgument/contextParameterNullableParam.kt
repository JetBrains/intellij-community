// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: No context argument for 'x: String?' found.

context(x: String?)
fun foo(a: String): String = a

fun main() {
    // String is assignable to String?, so the intention should be applicable
    foo(<caret>"Hello", a = "World")
}
