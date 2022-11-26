// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(p: Int, flag: Boolean){}

fun bar() {
    foo(1, <caret>)
}

// EXIST: { lookupString: "flag = true", itemText: "flag = true", attributes: "", icon: "Parameter"}
// EXIST: { lookupString: "flag = false", itemText: "flag = false", attributes: "", icon: "Parameter"}
