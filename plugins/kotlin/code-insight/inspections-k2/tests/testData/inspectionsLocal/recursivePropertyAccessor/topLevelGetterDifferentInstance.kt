// PROBLEM: none

interface Foo {
    val otherInstance: Foo
}

val Foo.bar: Any
    get() = otherInstance.bar<caret>
