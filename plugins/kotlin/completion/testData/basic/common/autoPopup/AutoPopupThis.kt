class Foo {
    fun String.bar() {
        this@<caret>
    }
}

// INVOCATION_COUNT: 0
// EXIST: this@bar