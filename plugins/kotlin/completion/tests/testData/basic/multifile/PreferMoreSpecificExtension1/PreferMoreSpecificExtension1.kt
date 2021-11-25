package ppp

import dependency.*

class C {
    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in dependency", typeText: "Int", icon: "nodes/function.svg"}
// NOTHING_ELSE
