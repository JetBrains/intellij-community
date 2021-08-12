// FIR_COMPARISON
abstract class Foo {
    abstract fun foo()
}

class Boo : Foo() {
    // 1
    /* 2 */ // 3
    fo<caret> /* 4 */ // 5
    // 6
}

// ELEMENT_TEXT: "override fun foo() {...}"
