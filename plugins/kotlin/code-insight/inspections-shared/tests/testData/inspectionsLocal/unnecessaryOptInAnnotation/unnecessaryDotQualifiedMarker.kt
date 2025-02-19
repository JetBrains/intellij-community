object Foo {
    @RequiresOptIn annotation class Bar
}

@OptIn(<caret>Foo.Bar::class) val x = null
