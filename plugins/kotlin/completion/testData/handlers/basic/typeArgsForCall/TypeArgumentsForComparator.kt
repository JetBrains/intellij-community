fun foo(): List<String> {
    return listOf<String>().sortedWith(
        compareBy { it.length }.<caret>
    )
}

// ELEMENT: reversed
// IGNORE_K1