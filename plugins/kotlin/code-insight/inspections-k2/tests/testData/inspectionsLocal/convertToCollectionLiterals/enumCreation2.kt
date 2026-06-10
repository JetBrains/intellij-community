// COMPILER_ARGUMENTS: -Xcollection-literals

enum class OfCollection(val col: Collection<String>) {
    E(emptyLi<caret>st()),
}

