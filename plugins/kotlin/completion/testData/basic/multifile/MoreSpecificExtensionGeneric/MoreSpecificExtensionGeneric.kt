// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(list: dependency.List<Int>) {
    list.xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(t: Int) for List<T> in dependency", typeText: "Unit", icon: "Function"}
// NOTHING_ELSE
