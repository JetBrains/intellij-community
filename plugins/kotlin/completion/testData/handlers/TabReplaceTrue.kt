fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>true)
}

// ELEMENT: sss
// CHAR: \t