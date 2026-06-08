// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments -XXLanguage:+ExplicitContextArguments
// LANGUAGE_VERSION: 2.3

// INTENTION_TEXT: Remove all possible argument names
context(x: String)
fun foo3(a: String, b: String): String {
    return x + a
}

fun main() {
    foo3(<caret>x = "Hello", a = "World", b = "a")
}