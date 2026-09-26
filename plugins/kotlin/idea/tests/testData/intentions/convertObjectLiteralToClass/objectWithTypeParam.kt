// WITH_STDLIB
class Foo<T> {
    val iter = obje<caret>ct: Iterable<T> {
        override fun iterator(): Iterator<T> = TODO()
    }
}