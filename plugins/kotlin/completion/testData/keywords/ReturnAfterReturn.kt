// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(a: Boolean) {
    if (a) return
    ret<caret>
}

// EXIST: { lookupString: "return", itemText: "return", tailText: null, attributes: "bold" }
