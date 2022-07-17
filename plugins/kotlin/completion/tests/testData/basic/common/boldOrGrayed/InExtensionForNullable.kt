fun String.forString(){}

fun globalFun(){}

fun String?.foo() {
    <caret>
}

// EXIST: { lookupString: "globalFun", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "nodes/function.svg"}
