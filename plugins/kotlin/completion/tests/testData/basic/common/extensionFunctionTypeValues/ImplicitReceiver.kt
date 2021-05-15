// FIR_COMPARISON
class C

fun C.test(foo: C.() -> Unit) {
    fo<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: null, typeText: "C.() -> Unit", icon: "nodes/parameter.svg"}
// ABSENT: { itemText: "foo", typeText: "Unit" }
