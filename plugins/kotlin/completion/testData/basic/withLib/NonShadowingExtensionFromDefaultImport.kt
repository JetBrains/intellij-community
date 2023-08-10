// FIR_COMPARISON
// FIR_IDENTICAL
// WITH_STDLIB
package ppp

fun test(s: String) {
    s.revers<caret>
}

// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in dependency", typeText: "String", icon: "Function"}
// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in kotlin.text", typeText: "String", icon: "Function"}