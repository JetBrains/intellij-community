class T

object TopLevel {
    object Nested {
        fun T.foo() {}
    }
}

fun T.usage() {
    f<caret>
}

// IGNORE_K2
// ELEMENT: foo