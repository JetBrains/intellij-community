// IGNORE_K1

enum class Enum {

    FOO
}

fun bar() {
    FO<caret>
}

// ELEMENT: FOO
// INVOCATION_COUNT: 2