// WITH_STDLIB
// IGNORE_K1
fun f(queries: List<String>) {
    for (query in queries) {
        q<caret>
    }
}

// ORDER: query
// ORDER: queries