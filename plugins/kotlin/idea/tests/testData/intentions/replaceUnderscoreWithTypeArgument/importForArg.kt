fun foo(c: kotlin.collections.AbstractIterator<kotlin.properties.ObservableProperty<Int>>) {
    bar<<caret>_>(c)
}

fun <T> bar(t: T): T = t

// WITH_STDLIB