// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun main() {
    run { mutableList<caret>Of<String>() }
}
