// COMPILER_ARGUMENTS: -Xcollection-literals

enum class OfCollection(val col: Collection<String>) {
    E(li<caret>stOf("a", "b")),
}

