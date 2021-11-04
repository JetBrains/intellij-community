// FIR_COMPARISON
interface A {
    fun foo(i: Int) {}
}

interface B {
    fun foo(i: Int) {}
}

fun A.bar() {}
fun B.bar() {}

class C : A, B {
    override fun foo(j: Int) {
        super.fo<caret>
    }
}

// EXIST: { lookupString:"foo", tailText:"(i: Int) for A" }
// EXIST: { lookupString:"foo", tailText:"(i: Int) for B" }
// EXIST: { lookupString:"foo", tailText:"(j) for A" }
// EXIST: { lookupString:"foo", tailText:"(j) for B" }
// ABSENT: bar
