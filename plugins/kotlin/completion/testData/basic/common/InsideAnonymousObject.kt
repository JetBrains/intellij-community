// IGNORE_K2

interface Foo {

    fun foo() {}
}

fun foo(): Foo = object : Foo {

    override fun foo() {
        bar<caret>
    }

    private fun bar() {}
}

// INVOCATION_COUNT: 0
// EXIST: bar