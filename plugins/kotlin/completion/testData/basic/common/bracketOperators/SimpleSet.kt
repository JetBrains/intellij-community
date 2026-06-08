class Test {
    operator fun set(a: Int, b: Int) {

    }
}

fun some(t: Test) {
    t.<caret>
}


// WITH_ORDER
// EXIST: { lookupString: "[]", itemText: "[]", tailText: "(a: Int, b: Int)", typeText: "Unit", icon: "Method", attributes: "bold"}
// EXIST: set