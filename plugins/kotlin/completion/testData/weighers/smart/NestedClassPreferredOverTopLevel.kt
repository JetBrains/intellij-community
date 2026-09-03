interface Foo

class FooImpl : Foo

class Bar {
    class NestedFoo : Foo
}

fun test() {
    val v: Foo = <caret>
}

// ORDER: FooImpl
// ORDER: NestedFoo
// ORDER: object
