// FIR_COMPARISON

fun String.baz(p: (String, Char) -> Unit) {}

fun foo() {
    "".baz<caret>
}

// WITH_ORDER
// EXIST: { lookupString:"baz", itemText: "baz", tailText: " { p: (String, Char) -> Unit } for String in <root>", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"baz", itemText: "baz", tailText: "(p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "Function"}
// NOTHING_ELSE