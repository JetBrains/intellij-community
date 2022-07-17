// WITH_STDLIB

public fun List<String>.fn() : List<String> {
    <caret>return map {
        if (it.isEmpty()) return emptyList()
        it
    }
}