// FIR_IDENTICAL
// FIR_COMPARISON
fun String.forString(){}

fun globalFun(){}

fun String?.foo() {
    <caret>
}

// EXIST: { lookupString: "globalFun", attributes: "", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "Function"}
