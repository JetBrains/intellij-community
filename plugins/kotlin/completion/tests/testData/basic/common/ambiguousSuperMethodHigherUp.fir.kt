// FIR_COMPARISON
interface A {
    fun foo(i: Int) {}
}

interface A2 : A

interface B {
    fun foo(j: Int) {}
}

class C : A2, B {
    fun test() {
        super.fo<caret>
    }
}

// EXIST: { lookupString:"foo", tailText:"(i: Int) for A2" }
// EXIST: { lookupString:"foo", tailText:"(j: Int) for B" }
// NOTHING_ELSE
