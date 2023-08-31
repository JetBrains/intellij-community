fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>123)
}

// IGNORE_K2
// ELEMENT: sss
// CHAR: \t