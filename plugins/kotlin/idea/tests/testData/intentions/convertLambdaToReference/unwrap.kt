// WITH_STDLIB

class Wrapper<T>(private val x: T) {
    fun unwrap() = x
}

val unwrapped = listOf(Wrapper(1), Wrapper("B")).map { <caret>it.unwrap() }