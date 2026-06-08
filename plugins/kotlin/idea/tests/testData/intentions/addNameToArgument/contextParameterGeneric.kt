// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'a' is never used

// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'List<String>', but 'String' was expected.
// K2_ERROR: No context argument for 'x: List<String>' found.

context(x: List<String>)
fun foo(a: String): String = a

fun main() {
    // List<String> matches List<String> context param
    foo(<caret>listOf("a", "b"), a = "World")
}
