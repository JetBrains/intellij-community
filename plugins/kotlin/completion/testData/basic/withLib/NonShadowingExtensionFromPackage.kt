// FIR_IDENTICAL
// FIR_COMPARISON
package ppp

fun String.reversed(): String = this

fun test(s: String) {
    s.revers<caret>
}

// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in dependency", typeText: "String", icon: "Function"}
// EXIST: { lookupString: "reversed", itemText: "reversed", tailText: "() for String in ppp", typeText: "String", icon: "Function"}
// NOTHING_ELSE