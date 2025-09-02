package test

class SomeTestClass(val a: Int) {
    constructor(b: String) : this(5) {}
}

fun getColor(): SomeTestClass {
    return SomeTestClas<caret>
}

// ELEMENT: SomeTestClass
// TAIL_TEXT: "(...) (test)"