package foo

class Some {
    constructor(v: Int): this()
}

fun foo() {
    val some = So<caret>
}

// IGNORE_K2
// ELEMENT: Some