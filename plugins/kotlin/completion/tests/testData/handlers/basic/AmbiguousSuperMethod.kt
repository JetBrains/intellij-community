// FIR_COMPARISON
package foo.bar
interface A {
    fun foo(i: Int) {}
}

interface B {
    fun foo(i2: Int) {}
}

class C : A, B {
    override fun foo(j: Int) {
        super.fo<caret>
    }
}

// ELEMENT: "foo"
// TAIL_TEXT: "(i: Int)"
