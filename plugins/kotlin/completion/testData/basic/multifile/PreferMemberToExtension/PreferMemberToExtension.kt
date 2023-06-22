// FIR_COMPARISON
package ppp

import dependency.*

class C {
    private fun xxx() {}

    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "Unit", icon: "Method"}
// NOTHING_ELSE
