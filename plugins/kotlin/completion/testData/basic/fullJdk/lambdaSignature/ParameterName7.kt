// IGNORE_K1

fun foo() {
    Triple(42, "", 0.0).let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "triple", tailText: " -> ", allLookupStrings: "triple", typeText: "Triple<Int, String, Double>" }
// EXIST: { itemText: "(first, second, third)", tailText: " -> ", allLookupStrings: "first, second, third", typeText: "(Int, String, Double)" }