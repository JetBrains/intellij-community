// FIR_IDENTICAL
// FIR_COMPARISON
package pack

class C

fun f() {
    C() <caret>
}

// ABSENT: "xxx"
// EXIST: { lookupString: "yyy", attributes: "bold", icon: "Function"}
// ABSENT: "zzz"
// ABSENT: "extensionProp"
