// COMPILER_ARGUMENTS: -Xcollection-literals
fun main() {
    list<caret>Of(
        1,
        2, // comment
        3,
        4,
        5,
    )
}