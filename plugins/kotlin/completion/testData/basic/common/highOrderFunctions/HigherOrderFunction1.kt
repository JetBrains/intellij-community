// FIR_COMPARISON

fun foo(p: (String, Char) -> Unit) {}

fun test() {
    foo<caret>
}

// WITH_ORDER
// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { String, Char -> ... } (p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
