// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

class C {
    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Any in dependency1", typeText: "Int", icon: "Function"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in dependency2", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
