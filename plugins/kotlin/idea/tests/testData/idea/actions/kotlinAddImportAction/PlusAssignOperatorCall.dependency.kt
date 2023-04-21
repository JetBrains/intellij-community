package pack

class Arr<T> : Iterable<T> {
    override fun iterator(): Iterator<T> = TODO()
}

operator fun <T> Arr<T>.plusAssign(element: T): Unit = TODO()
