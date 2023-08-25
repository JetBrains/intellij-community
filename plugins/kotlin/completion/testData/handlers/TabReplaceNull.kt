fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>null)
}

// IGNORE_K2
// ELEMENT: sss
// CHAR: \t