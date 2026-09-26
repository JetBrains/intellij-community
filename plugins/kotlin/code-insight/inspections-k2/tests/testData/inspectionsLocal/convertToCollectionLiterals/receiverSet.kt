// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun main() {
    se<caret>tOf(true, false).forEach { print(it) }
}