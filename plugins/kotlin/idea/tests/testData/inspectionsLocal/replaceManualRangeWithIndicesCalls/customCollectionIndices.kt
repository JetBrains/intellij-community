// WITH_STDLIB
// FIX: Replace with indices
class CustomCollection<T>(private val elements: List<T>) : AbstractCollection<T>() {
    override val size: Int get() = elements.size
    operator fun get(index: Int): T = elements[index]
    override fun iterator(): Iterator<T> = elements.iterator()
}

fun test(collection: CustomCollection<String>) {
    for (i in 0 until <caret>collection.size) {
        doSomething(i)
    }
}

fun doSomething(i: Int) {}
