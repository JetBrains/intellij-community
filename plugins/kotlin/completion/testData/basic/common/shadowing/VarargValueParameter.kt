// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

class C

class A {
    fun C.xxx(vararg s: String) = ""

    fun C.test() {
        xx<caret>
    }
}

fun C.xxx(vararg s: String) = ""
fun C.xxx(s: String) = ""

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(vararg s: String) for C in A", typeText: "String"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(s: String) for C in ppp", typeText: "String"}
// NOTHING_ELSE