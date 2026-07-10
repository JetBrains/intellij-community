// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'x' is never used
// K2_ERROR: TOO_MANY_ARGUMENTS


context(x: String)
fun foo(): String = x

fun main() {
    with("context") {
        foo(<caret>"Hello")
    }
}
