// PROBLEM: none
class Foo()
class Bar() {
    private operator fun Foo.<caret>plus(other: Foo): Foo = other  // marked as unused
    var foo = Foo()
    fun foo() {
        foo += foo
    }
}