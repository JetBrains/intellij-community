fun foo(list: List<String>) {
    list.map { Stri<caret> }
}

// IGNORE_K2
// ELEMENT: String
// TAIL_TEXT: " (kotlin)"