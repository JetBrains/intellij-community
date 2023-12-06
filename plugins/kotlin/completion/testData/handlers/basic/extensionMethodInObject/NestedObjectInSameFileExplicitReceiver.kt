class T

object TopLevel {
    object Nested {
        fun T.foo() {}
    }
}

fun usage(t: T) {
    t.f<caret>
}

// IGNORE_K2
// ELEMENT: foo