fun coroutine(block: suspend () -> Unit) {}

class SuspendIterable {
    operator fun iterator() = this
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
    suspend fun test() {}
}

fun foo() {
    val iterable = SuspendIterable()
    coroutine {
        iterable.<lineMarker descr="Suspend function call">test</lineMarker>()
        for (x in <lineMarker descr="Suspending iteration">iterable</lineMarker>)
            println(x)
    }
}