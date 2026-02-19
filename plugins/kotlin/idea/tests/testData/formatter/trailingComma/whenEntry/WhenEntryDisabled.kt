fun foo(x: Any) = when (x) {
    when (x) {
        Comparable::class, Iterable::class,
        String::class /*// trailing comma*/ -> println(1)

        else -> println(3)
    }
}

// SET_TRUE: ALLOW_TRAILING_COMMA
// SET_FALSE: ALLOW_TRAILING_COMMA_WHEN_ENTRY