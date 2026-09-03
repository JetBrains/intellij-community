package testdata

interface Foo

class AlreadyImported

class TopLevelFoo : Foo

class Container {
    class NestedFoo : Foo
}
