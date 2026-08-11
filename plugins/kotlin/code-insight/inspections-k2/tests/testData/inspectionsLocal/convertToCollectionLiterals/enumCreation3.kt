// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

enum class OfCollection(val col: Collection<String>) {
    E(se<caret>tOf("a", "b")),
}

