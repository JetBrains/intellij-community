interface First
interface Second

fun test(valueA: Any, valueZ: Any): Second {
    if (valueZ is First && valueZ is Second) {
        return value<caret>
    }
    error("")
}

// ORDER: valueZ
// ORDER: valueA
