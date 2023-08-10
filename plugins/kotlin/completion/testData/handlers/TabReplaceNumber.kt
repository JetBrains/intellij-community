fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>123)
}

// ELEMENT: sss
// CHAR: \t