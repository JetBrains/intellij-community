interface First
interface Second

fun test(value: Any): First {
    if (value is First && value is Second) {
        return val<caret>
    }
    error("")
}

// EXIST: { lookupString: "value", typeText: "First" }
