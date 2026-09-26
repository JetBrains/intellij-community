// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String)
fun foo2(a: String): String {
    return x + a
}

fun main() {
    // value param 'a' should be suggested instead of context param 'x'
    foo2("World", <caret>"a")
}