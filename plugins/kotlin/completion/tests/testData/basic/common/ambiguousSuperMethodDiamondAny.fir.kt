// FIR_COMPARISON
interface A {
    fun foo() {}
}

interface B1 : A
interface B2 : A

class C : B1, B2 {
    fun test() {
        super.hash<caret>
    }
}

// EXIST: { lookupString:"hashCode", tailText:"()" }
// NOTHING_ELSE
