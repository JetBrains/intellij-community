enum class Enum {
    FOO
}

fun foo(a: Enum) {

}

fun bar() {
    foo(<caret>)
}

// WITH_ORDER
// EXIST: FOO