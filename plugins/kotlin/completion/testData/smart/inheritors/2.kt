fun foo(): List<String> {
    return <caret>
}

// INVOCATION_COUNT: 2
// EXIST: { lookupString: "ArrayList", itemText: "ArrayList", tailText: "(...) (java.util)" }

// IGNORE_K2
