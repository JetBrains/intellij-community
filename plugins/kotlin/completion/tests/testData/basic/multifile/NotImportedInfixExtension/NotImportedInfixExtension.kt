// FIR_COMPARISON
package pack

class C

fun f() {
    C() <caret>
}

// ABSENT: "xxx"
// EXIST: { lookupString: "yyy", attributes: "bold", icon: "nodes/function.svg"}
// ABSENT: "zzz"
// ABSENT: "extensionProp"
