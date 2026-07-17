// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'a' is never used
// K2_ERROR: ARGUMENT_PASSED_TWICE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_CONTEXT_ARGUMENT


context(x: Unit)
fun foo(a: String): String = a

fun main() {
    // Unit matches Unit context param
    foo(<caret>Unit, a = "World")
}
