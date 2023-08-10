// WITH_STDLIB

fun foo(value: Int?): Int? {
    return value<caret>?.let { baz(it) }
}

fun baz(i: Int) = i + i