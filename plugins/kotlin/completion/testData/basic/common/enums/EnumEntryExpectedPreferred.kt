enum class Enum {
    FOO
}

fun bar() {
    val a: Enum = <caret>
}

// WITH_ORDER
// EXIST: FOO