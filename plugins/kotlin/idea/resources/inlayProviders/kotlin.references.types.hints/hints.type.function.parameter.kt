fun hintsDemo() {
    listOf(1, 2, 3).filter { elem -> // parameter with inferred type
        elem >= 3
    }
}

class List<T> {
    fun filter<R>(op: (T) -> Boolean) : List<R> = TODO()
}

fun <T> listOf(vararg elements: T): List<T> = TODO()

class Int
class Boolean