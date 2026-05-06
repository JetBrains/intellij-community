// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No value passed for parameter 'a'.
// K2_ERROR: Unresolved reference 'y'.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No context argument for 'x: String' found.
// K2_AFTER_ERROR: Unresolved reference 'y'.
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Mixing named and positioned arguments is not allowed
// ERROR: Mixing named and positioned arguments is not allowed
// ERROR: No value passed for parameter 'a'
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: y

context(x: String)
fun foo(a: String, b: String): String = x + y + a

fun main() {
    // no intention suggestion because it will be covered by diagnostics MixingNamedAndPositionalArguments fix (AddNameToArgumentFixFactory)
    foo(b = "World", <caret>"Hello", "a")
}