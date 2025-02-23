fun foo() {
    listOf(1, 2, 3).let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "ints", tailText: " -> ", allLookupStrings: "ints", typeText: "List<Int>" }
// ABSENT: { lookupString: "i", itemText: "(i, i1, i2, i3, i4)", tailText: " -> ", allLookupStrings: "i, i1, i2, i3, i4", typeText: "(Int, Int, Int, Int, Int)" }