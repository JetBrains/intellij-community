// IGNORE_K1

fun foo() {
    Pair(42, "").let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "pair", tailText: " -> ", allLookupStrings: "pair", typeText: "Pair<Int, String>" }
// EXIST: { itemText: "(first, second)", tailText: " -> ", allLookupStrings: "first, second", typeText: "(Int, String)" }