package classCompletionImport

fun test(): Int? {
        return 5 as? SortedSe<caret>if (true) 5
}

// ELEMENT: SortedSet
// USE_EXPENSIVE_RENDERER
