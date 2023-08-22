// FIR_IDENTICAL
// FIR_COMPARISON
interface A {
    fun foo(i: Int)
}

interface B {
    fun foo(i: Int) {}
}

class C : A, B {
    fun test() {
        super.fo<caret>
    }
}

// EXIST: { lookupString:"foo", tailText:"(i: Int)" }
// NOTHING_ELSE
