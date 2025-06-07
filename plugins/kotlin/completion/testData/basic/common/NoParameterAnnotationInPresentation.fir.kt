// FIR_COMPARISON
fun foo(@Suppress("UNCHECKED_CAST") p: () -> Unit) {}

fun bar() {
    <caret>
}

// WITH_ORDER
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { p: () -> Unit } (<root>)", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: () -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
