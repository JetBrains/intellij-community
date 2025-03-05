// IGNORE_K2

object Foo {

    private enum class Bar {

        BAR,
    }
}

fun foo() {
    B<caret>
}

// ABSENT: BAR
// INVOCATION_COUNT: 2