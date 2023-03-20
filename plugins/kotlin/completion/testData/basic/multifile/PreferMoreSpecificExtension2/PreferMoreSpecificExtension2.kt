// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

class C {
    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in dependency", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
