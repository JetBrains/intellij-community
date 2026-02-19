// PROBLEM: none
// WITH_STDLIB

class Foo : Iterable<Int> {
    override fun iterator(): Iterator<Int> = listOf(1).iterator()
}

fun foo() {
    Foo().<caret>count()
}
