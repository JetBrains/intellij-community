// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments

// PRIORITY: LOW
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No context argument for 'x: String' found.
// K2_ERROR: No value passed for parameter 'a'.

context(x: String)
fun foo(a: String, b: String): String {
    return x + a
}

fun main() {
    foo(b = "World", <caret>"Hello", "a")
}