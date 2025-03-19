// FIR_COMPARISON

fun baz(p: (String, Char) -> Unit) {}

fun foo() {
    baz<caret>
}

// WITH_ORDER
// EXIST: { lookupString:"baz", itemText: "baz", tailText: " { p: (String, Char) -> Unit } (<root>)", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"baz", itemText: "baz", tailText: "(p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
// NOTHING_ELSE
