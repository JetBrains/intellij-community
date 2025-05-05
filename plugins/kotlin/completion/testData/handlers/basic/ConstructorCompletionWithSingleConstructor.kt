package test

class SomeTestClass(val a: Int)

fun getColor(): SomeTestClass {
    return SomeTestClas<caret>
}

// ELEMENT: SomeTestClass
// TAIL_TEXT: "(a: Int) (test)"