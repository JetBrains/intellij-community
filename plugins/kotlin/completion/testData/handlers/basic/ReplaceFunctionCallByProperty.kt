class C {
    val bar: Int
}

fun foo(c: C) {
    val v = c.<caret>getBar()
}

// IGNORE_K2
// ELEMENT: bar
// CHAR: '\t'
