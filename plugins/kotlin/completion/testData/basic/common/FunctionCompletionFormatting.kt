// FIR_IDENTICAL
// FIR_COMPARISON

fun test(a: Int) {}

fun some() {
    tes<caret>
}

// EXIST: { lookupString:"test", itemText:"test", tailText:"(a: Int) (<root>)", icon: "Function"}