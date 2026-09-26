// FIR_IDENTICAL
open class Foo {
    open val v: Int
        get() = 10
}

class Bar: Foo() {
<caret>
}

// MEMBER: "v: Int"