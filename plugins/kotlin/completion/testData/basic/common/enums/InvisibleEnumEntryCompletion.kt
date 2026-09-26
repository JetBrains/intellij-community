

object Foo {

    private enum class Bar {

        BAR,
    }
}

fun foo() {
    B<caret>
}

// EXIST: BAR
// INVOCATION_COUNT: 3