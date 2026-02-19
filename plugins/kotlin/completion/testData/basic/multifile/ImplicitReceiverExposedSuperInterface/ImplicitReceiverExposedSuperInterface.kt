fun main() {
    fooBar {
        foo<caret>
    }
}

// INVOCATION_COUNT: 0
// EXIST: foo