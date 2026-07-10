// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments
context(s: String)
fun foo(): String {
    return s
}

fun main() {
    foo(<caret>s = "")
}