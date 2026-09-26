// WITH_STDLIB
class Bar<T> {
    class B {
        val iter = obje<caret>ct : Iterable<Int> {
            override fun iterator(): Iterator<Int> = TODO()
        }
    }
}