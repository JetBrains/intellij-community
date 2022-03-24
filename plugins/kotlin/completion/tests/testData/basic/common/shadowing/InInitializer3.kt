// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(xxx: Int) {
    val xxx: Any = run {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", typeText: "Int", icon: "nodes/parameter.svg"}
// NOTHING_ELSE
