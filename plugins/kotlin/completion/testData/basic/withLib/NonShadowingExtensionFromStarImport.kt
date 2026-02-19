// FIR_IDENTICAL
// FIR_COMPARISON
package ppp

import dependency1.*

fun test(s: String) {
    s.revers<caret>
}

// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in dependency1", typeText: "String", icon: "Function"}
// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in dependency2", typeText: "String", icon: "Function"}
// NOTHING_ELSE