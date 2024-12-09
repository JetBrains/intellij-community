// FIR_COMPARISON

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "entry ->", allLookupStrings: "entry ->" }
// EXIST: { itemText: "entry: Map.Entry<String, Int> ->", allLookupStrings: "entry: Map.Entry<String, Int> ->" }