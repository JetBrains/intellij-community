interface Foo

class TopLevelFoo : Foo

class Container {
    class NestedFoo : Foo
}

fun test() {
    class LocalFoo : Foo

    val value: Foo = <caret>
}

// ORDER: LocalFoo
// ORDER: TopLevelFoo
// ORDER: NestedFoo
