// FIR_COMPARISON

fun String.foo(p: (String, Char) -> Unit) {}

fun test() {
    "".fo<caret>
}

// WITH_ORDER
// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { String, Char -> ... } (p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "Function"}
