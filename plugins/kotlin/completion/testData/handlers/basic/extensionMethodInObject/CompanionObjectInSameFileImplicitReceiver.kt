class T {
    companion object {
        fun T.foo() {}
    }
}

fun T.usage() {
    f<caret>
}

// IGNORE_K2
// ELEMENT: foo