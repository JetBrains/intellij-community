// COMPILER_ARGUMENTS: -Xcollection-literals
val list = buildList {
        add(1)
        addAll(list<caret>Of(2, 3))
    }