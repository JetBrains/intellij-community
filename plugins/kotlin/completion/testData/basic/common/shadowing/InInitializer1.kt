// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(xxx: String) {
    var xxx: Int = xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", typeText: "String", icon: "Parameter"}
// NOTHING_ELSE
