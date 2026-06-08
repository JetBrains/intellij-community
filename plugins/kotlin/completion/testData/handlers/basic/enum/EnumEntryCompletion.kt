

enum class Enum {

    FOO
}

fun bar() {
    FO<caret>
}

// ELEMENT: FOO
// INVOCATION_COUNT: 2