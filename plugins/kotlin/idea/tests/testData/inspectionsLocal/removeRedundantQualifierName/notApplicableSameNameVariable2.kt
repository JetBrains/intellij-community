// PROBLEM: none
// WITH_STDLIB
sealed class Foo {
    object BAR : Foo()

    companion object {
        val BAR: Foo by lazy { <caret>Foo.BAR }
    }
}