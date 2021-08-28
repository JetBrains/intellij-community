// FIR_COMPARISON
open class Base {
    open fun foo(p1: Int, vararg p2: Int){}
}

class Derived : Base() {
    override fun foo(p3: Int, vararg p4: Int) {
        super.<caret>
    }
}

// ELEMENT: foo
// TAIL_TEXT: "(p3, *p4)"
