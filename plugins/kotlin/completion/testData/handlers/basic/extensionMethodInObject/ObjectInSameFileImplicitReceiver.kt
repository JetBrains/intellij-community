class T

object Extensions {
    fun T.foo() {}
}

fun T.usage() {
    f<caret>
}

// IGNORE_K2
// ELEMENT: foo