// FIR_IDENTICAL
// FIR_COMPARISON

enum class Enum {

    FOO
}

fun bar() {
    <caret>
}

// ABSENT: FOO
// ABSENT: Enum.FOO
// INVOCATION_COUNT: 2