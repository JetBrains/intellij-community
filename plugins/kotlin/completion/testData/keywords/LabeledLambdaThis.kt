// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(): String.() -> Unit {
    return (label@ {
        f {
            thi<caret> // TODO too many lookup elements for an empty prefix
        }
    })
}

fun f(p: Any.() -> Unit){}

// INVOCATION_COUNT: 1
// EXIST: { lookupString: "this", itemText: "this", tailText: null, typeText: "Any", attributes: "bold" }
// ABSENT: "this@f"
// EXIST: { lookupString: "this@label", itemText: "this", tailText: "@label", typeText: "String", attributes: "bold" }
