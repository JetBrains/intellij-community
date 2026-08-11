// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Too many arguments for public fun foo(a: Boolean, b: String): String defined in root package in file contextParameterNoOptLeftInMapping.kt
// ERROR: Unresolved reference: x
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS

context(x: Boolean)
fun foo(a: Boolean, b: String): String {
    return " " + x + b
}
fun main() {
    // "b =" would be the only option, but it's already mapped to "Hello", so nothing to suggest
    foo(true, "Hello", <caret>"a")
}