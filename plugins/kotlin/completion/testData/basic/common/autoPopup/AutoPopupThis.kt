class Foo {
    fun String.bar() {
        this@<caret>
    }
}

// IGNORE_K2
// INVOCATION_COUNT: 0
// EXIST: this@bar