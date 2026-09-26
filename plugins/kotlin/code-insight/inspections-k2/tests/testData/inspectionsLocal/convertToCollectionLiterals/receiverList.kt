// COMPILER_ARGUMENTS: -Xcollection-literals

fun main() {
    lis<caret>tOf(true, false).forEach { print(it) }
}