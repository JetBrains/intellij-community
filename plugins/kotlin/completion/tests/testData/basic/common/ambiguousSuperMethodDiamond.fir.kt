// FIR_COMPARISON
interface A {
    fun foo() {}
}

interface B1 : A
interface B2 : A

class C : B1, B2 {
    fun test() {
        super.fo<caret>
    }
}

//
// EXIST: { lookupString:"foo", tailText:"() for B1" }
// EXIST: { lookupString:"foo", tailText:"() for B2" }
// NOTHING_ELSE
