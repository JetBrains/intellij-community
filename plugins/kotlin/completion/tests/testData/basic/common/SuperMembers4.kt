// FIR_IDENTICAL
// FIR_COMPARISON
open class B {
    open fun foo() {}
    open fun bar() {}
}

class C : B() {
    override fun foo() {
        super.<caret>
    }
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "()", typeText: "Unit", attributes: "bold", icon: "nodes/method.svg"}
// EXIST: { lookupString: "bar", itemText: "bar", tailText: "()", typeText: "Unit", attributes: "bold", icon: "nodes/method.svg"}
// EXIST: equals
// EXIST: hashCode
// EXIST: toString
// NOTHING_ELSE
