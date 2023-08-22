// FIR_COMPARISON
package ppp

import dependency1.xxx

fun Int.xxx() {}

fun Int.test() {
    xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Int in dependency1", typeText: "Unit", icon: "Function"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Int in dependency2", typeText: "Unit", icon: "Function"}
// NOTHING_ELSE
