fun foo(list: List<String>) {
    list.filter {
        it.<caret>
    }
}

// ELEMENT: isEmpty
// IGNORE_K2
