// WITH_STDLIB
// PROBLEM: none
class CustomIterable<T>(private val elements: List<T>) : Iterable<T> {
    val size: Int get() = elements.size
    val indices: String = "wrong type"  // Wrong return type!
    operator fun get(index: Int): T = elements[index]
    override fun iterator(): Iterator<T> = elements.iterator()
}

fun test(iterable: CustomIterable<String>) {
    for (i in 0 until <caret>iterable.size) {
        doSomething(i)
    }
}

fun doSomething(i: Int) {}