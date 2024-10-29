package test

import test.Outer.Nested

class Outer {
    fun interface Nested {
        fun foo()
    }
}

fun usage() {
    Nested {}
}