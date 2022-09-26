suspend fun test(iterable: MyIterable) {
    <lineMarker text="&lt;html&gt;Suspend operator call &apos;next()&apos;&lt;hr size=1 noshade&gt;Suspend operator call &apos;hasNext()&apos;&lt;/html&gt;">for</lineMarker> (value in iterable) {
        print(value)
    }
}

class MyIterable {
    operator fun iterator() = this
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
}