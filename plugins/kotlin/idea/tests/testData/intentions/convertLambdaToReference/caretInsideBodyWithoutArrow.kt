// IS_APPLICABLE: true

fun foo(strings: List<String>) =
    strings.map { it.hashCode(<caret>) }

