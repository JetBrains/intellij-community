// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun main() {
    [<caret>
        1,
        2, // comment
        3,
        4,
        5,
    ]
}