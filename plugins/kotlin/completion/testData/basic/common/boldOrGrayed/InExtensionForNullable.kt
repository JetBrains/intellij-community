// FIR_IDENTICAL
// FIR_COMPARISON
fun String.cforString() {}

fun cglobalFun() {}

fun String?.foo() {
    c<caret>
}

// EXIST: { lookupString: "cglobalFun", attributes: "", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "cforString", attributes: "grayed", icon: "Function"}
// todo too many items!
// todo remove c prefix
