// IGNORE_K1
interface A {
    fun foo(lambda: () -> Unit) {}
}

interface B {
    fun foo(lambda: () -> Unit) {}
}

fun A.bar() {}
fun B.bar() {}

class C : A, B {
    override fun foo(lambda: () -> Unit) {
        super.fo<caret>
    }
}

// WITH_ORDER
// EXIST: { lookupString:"foo", tailText:"(lambda) for A" }
// EXIST: { lookupString:"foo", tailText:"(lambda) for B" }
// EXIST: { lookupString:"foo", tailText:" { lambda: () -> Unit } for A" }
// EXIST: { lookupString:"foo", tailText:" { lambda: () -> Unit } for B" }
// EXIST: { lookupString:"foo", tailText:"(lambda: () -> Unit) for A" }
// EXIST: { lookupString:"foo", tailText:"(lambda: () -> Unit) for B" }
// NOTHING_ELSE
