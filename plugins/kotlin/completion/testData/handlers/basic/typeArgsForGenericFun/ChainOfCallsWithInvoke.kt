// The goal is to check that type parameter is inserted for the right call.

class SimpleClass<T>(val f: T) {
    operator fun <U> invoke(): List<U> = TODO()
    fun self(): SimpleClass<T> = TODO()

    val one: List<Int> = SimpleClass("one").self().self()().<caret>
}

// ELEMENT: asReversed