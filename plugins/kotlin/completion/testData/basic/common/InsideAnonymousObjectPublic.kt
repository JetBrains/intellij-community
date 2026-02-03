interface Foo {

    fun foo() {}
}

fun foo(): Foo = object : Foo {

    override fun foo() {
        bar<caret>
    }

    fun bar() {}
}

// INVOCATION_COUNT: 0
// EXIST: bar