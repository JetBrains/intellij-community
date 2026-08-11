// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Too many arguments for public fun foo(a: String, b: String): String defined in root package in file contextParameterAmbiguous.kt
// ERROR: Unresolved reference: with
// ERROR: Unresolved reference: x
// AFTER-WARNING: Parameter 'b' is never used
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: TOO_MANY_ARGUMENTS

context(x: String)
fun foo(a: String, b: String): String = x + a

fun main() {
    with("context") {
        foo(a = "World", <caret>"Hello", "Hola")
    }
}