// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments -XXLanguage:+ExplicitContextArguments
// LANGUAGE_VERSION: 2.3

context(x: String, y: Int)
fun foo(a: String, b: String): String {
    return x + a + y
}

fun main() {
    foo(<caret>x = "Hello", y = 42, a = "World", b = "!")
}
