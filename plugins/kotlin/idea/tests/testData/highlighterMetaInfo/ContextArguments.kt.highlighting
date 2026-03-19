// IGNORE_K1
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
context(x: String)
fun foo(a: String): String {
    return x + a
}

fun main() {
    foo(x = "Hello", a = "World")
}