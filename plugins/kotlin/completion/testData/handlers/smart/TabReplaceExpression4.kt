fun foo(s: String){ }

fun bar(sss: String) {
    foo(<caret>x())
}

//ELEMENT: sss
//CHAR: \t
