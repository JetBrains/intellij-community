// IGNORE_K1
// IGNORE_K2

fun foo() {
    emptyMap<String, Int>()
        .forEach { t, <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "u", tailText: " -> ", allLookupStrings: "u", typeText: "Int" }