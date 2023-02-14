// PROBLEM: none
// WITH_STDLIB

class Foo {
    fun count() = 0
}

fun foo() {
    Foo().<caret>count()
}
