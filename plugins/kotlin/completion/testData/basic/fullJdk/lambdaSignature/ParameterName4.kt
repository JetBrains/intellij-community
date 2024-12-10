// IGNORE_K1

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "entry", tailText: " -> ", allLookupStrings: "entry", typeText: "Map.Entry<String, Int>" }
// EXIST: { itemText: "mapEntry", tailText: " -> ", allLookupStrings: "mapEntry", typeText: "Map.Entry<String, Int>" }
// EXIST: { itemText: "(key, value)", tailText: " -> ", allLookupStrings: "key, value", typeText: "(String, Int)" }
/* TODO EXIST: { itemText: "t, u", tailText: " -> ", allLookupStrings: "t, u", typeText: "(String, Int)" } */