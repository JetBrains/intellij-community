fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>true)
}

// IGNORE_K2
// ELEMENT: sss
// CHAR: \t