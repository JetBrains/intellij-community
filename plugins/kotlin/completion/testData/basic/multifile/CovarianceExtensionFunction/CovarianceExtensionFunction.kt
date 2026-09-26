package bar

import foo.*

data class Bar<T>(val value: T)

fun Bar<out Foo>.foo(): Unit =
    value.fo<caret>

// EXIST: foo