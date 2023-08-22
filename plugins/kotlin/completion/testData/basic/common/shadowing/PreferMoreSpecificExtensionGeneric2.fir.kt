// FIR_COMPARISON
fun Int.xxx() {}

fun <T> Int.xxx(): T = t

fun <T> T.xxx(): T = t

fun test() {
    1.xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Int in <root>", typeText: "Unit", icon: "Function", attributes: "bold"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Int in <root>", typeText: "T", icon: "Function", attributes: "bold"}
// NOTHING_ELSE
