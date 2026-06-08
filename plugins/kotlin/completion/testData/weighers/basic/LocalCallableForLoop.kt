// WITH_STDLIB

fun f(queries: List<String>) {
    for (query in queries) {
        q<caret>
    }
}

// ORDER: query
// ORDER: queries