import kotlin.collections.distinct as unique

fun foo() {
    listOf(1, 2, 3).<caret>
}

// IGNORE_K2
// ELEMENT: "unique"