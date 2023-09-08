// FIR_COMPARISON
interface I
interface J

fun test(p: I, fooI: I.() -> Unit, fooJ: J.() -> Unit) {
    if (p is J) {
        p.fo<caret>
    }
}

// EXIST: { lookupString: "fooI", itemText: "fooI", tailText: "() for I", typeText: "Unit", attributes: "bold", icon: "Parameter"}
// EXIST: { lookupString: "fooJ", itemText: "fooJ", tailText: "() for J", typeText: "Unit", attributes: "bold", icon: "Parameter"}
// IGNORE_K2