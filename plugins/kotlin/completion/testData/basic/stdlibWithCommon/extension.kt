// FIR_COMPARISON
// FIR_IDENTICAL
// IGNORE_K2

fun test() {
    listOf(1).forE<caret>
}

// EXIST: { lookupString: "forEach", itemText: "forEach", "tailText":" {...} (action: (Int) -> Unit) for Iterable<T> in kotlin.collections","typeText":"Unit","icon":"Function","attributes":"","allLookupStrings":"forEach","itemText":"forEach"}
