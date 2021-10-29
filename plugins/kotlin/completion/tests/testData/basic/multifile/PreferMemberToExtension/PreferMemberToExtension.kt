package ppp

import dependency.*

class C {
    private fun xxx() {}

    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "Unit", icon: "nodes/method.svg"}
// NOTHING_ELSE
