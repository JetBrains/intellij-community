// IS_APPLICABLE: true

fun <K> bar(x: K & Any) = foo<K<caret> & Any>(x)

fun <T> foo(l: T) = l
