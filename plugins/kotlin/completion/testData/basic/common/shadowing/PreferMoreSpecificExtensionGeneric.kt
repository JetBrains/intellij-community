// FIR_COMPARISON
// FIR_IDENTICAL
fun <T> List<T>.xxx(){}
fun <T> Iterable<T>.xxx(){}

fun foo() {
    listOf(1).xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for List<T> in <root>", typeText: "Unit", icon: "Function"}
// NOTHING_ELSE
