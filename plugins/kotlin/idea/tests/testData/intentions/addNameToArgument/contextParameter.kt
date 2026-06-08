// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// PRIORITY: LOW
// INTENTION_TEXT: "Add 'x =' to argument"
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'x' is never used

// K2_ERROR: Argument already passed for this parameter.

context(x: String)
fun foo(a: String): String = x + a

fun main() {
    with("context") {
        foo(<caret>"Hello", a = "World")
    }
}