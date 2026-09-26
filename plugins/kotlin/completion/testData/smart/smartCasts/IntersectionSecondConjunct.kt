interface First
interface Second

fun test(value: Any): Second {
    if (value is First && value is Second) {
        return val<caret>
    }
    error("")
}

// EXIST: { lookupString: "value", typeText: "Second" }
