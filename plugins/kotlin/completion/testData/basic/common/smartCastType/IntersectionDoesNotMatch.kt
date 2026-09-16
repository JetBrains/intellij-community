interface First
interface Second
interface Third

fun test(value: Any): Third {
    if (value is First && value is Second) {
        return val<caret>
    }
    error("")
}

// EXIST: { lookupString: "value", typeText: "Any" }
