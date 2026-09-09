fun test(value: Any) {
    if (value is String) {
        val result = val<caret>
    }
}

// EXIST: { lookupString: "value", typeText: "Any" }
