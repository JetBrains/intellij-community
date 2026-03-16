class Foo<T> {
    inner class B { // TARGET_BLOCK:
        val iter = ob<caret>ject: Iterable<T> {
            override fun iterator(): Iterator<T> = throw MyNotImplementedError()
        }
    }
}
class MyNotImplementedError : RuntimeException()