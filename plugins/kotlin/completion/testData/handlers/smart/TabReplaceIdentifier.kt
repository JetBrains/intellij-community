fun foo(s: String){ }

fun bar(ss: String) {
    foo(<caret>xxx)
}

//ELEMENT: ss
//CHAR: \t

// IGNORE_K2
