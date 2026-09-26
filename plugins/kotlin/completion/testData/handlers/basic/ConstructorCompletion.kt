package test

class SomeTestClass

fun getColor(): SomeTestClass {
    return SomeTestClas<caret>
}

// ELEMENT: SomeTestClass
// TAIL_TEXT: "() (test)"