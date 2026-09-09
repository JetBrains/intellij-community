interface First
interface Second

fun test(value: Any?): Second {
    if (value is First? && value is Second? && value != null) {
        return val<caret>
    }
    error("")
}

// EXIST: { lookupString: "value", typeText: "Second" }
