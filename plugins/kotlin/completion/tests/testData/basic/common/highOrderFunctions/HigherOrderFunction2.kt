fun String.foo(p: (String, Char) -> Unit){}

fun test() {
    "".fo<caret>
}

// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "nodes/function.svg"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { String, Char -> ... } (p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "nodes/function.svg"}
