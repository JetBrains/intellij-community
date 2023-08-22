fun test(foo: Foo?) {
    <caret>if (foo != null) foo.invoke()
}

class Foo {
    operator fun invoke() {}
}