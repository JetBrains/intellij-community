fun xfoo(p: (String, Char) -> Unit) {}

fun test() {
    ::xfo<caret>
}

// EXIST: { lookupString:"xfoo", itemText: "xfoo", tailText: "(p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
// NOTHING_ELSE
