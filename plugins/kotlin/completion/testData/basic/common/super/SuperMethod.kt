// FIR_COMPARISON
open class A() {
    fun foo1()
    open fun foo2(x: Int)
    open fun foo3(x: Int)
}

class B() : A() {
    override fun foo2(y: Int) {
        super.foo<caret>
    }
}

// EXIST: { lookupString: "foo1", tailText: "()" }
// EXIST: { lookupString: "foo2", tailText: "(x)" }
// EXIST: { lookupString: "foo2", tailText: "(x: Int)" }
// EXIST: { lookupString: "foo3", tailText: "(x: Int)" }
// NOTHING_ELSE
