suspend fun test(iterable: MyIterable) {
    <lineMarker text="Suspend operator call 'next()'&lt;hr size=1 noshade&gt;Suspend operator call 'hasNext()'">for</lineMarker> (value in iterable) {
        print(value)
    }
}

class MyIterable {
    operator fun iterator() = this
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
}