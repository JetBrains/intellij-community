interface First
interface Second

fun test(value: First): Second {
    if (value is Second) {
        return val<caret>
    }
    error("")
}

// EXIST: { lookupString: "value", typeText: "Second" }
