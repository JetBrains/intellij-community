// FIR_IDENTICAL
// FIR_COMPARISON
class C {
    val xxx = 1

    fun foo(xxx: String) {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", typeText: "String", icon: "Parameter"}
// NOTHING_ELSE
