// WITH_STDLIB
class Foo<T> {
    inner class B {
        val iter = ob<caret>ject: Iterable<T> {
            override fun iterator(): Iterator<T> = TODO()
        }
    }
}