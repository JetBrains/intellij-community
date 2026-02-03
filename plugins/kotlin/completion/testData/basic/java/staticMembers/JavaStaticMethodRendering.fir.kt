// FIR_COMPARISON

fun foo() {
    System.current<caret>
}

// EXIST: { itemText: "currentTimeMillis", lookupString: "currentTimeMillis", tailText: "()", typeText: "Long", icon: "Method", attributes: "bold" }