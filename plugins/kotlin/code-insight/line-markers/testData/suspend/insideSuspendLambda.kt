suspend fun test() {
    coroutine {
        <lineMarker text="Suspend function call 'foo()'">foo</lineMarker>()
    }
}

fun coroutine(block: suspend () -> Unit) {
    <lineMarker text="Suspend operator call 'invoke()'">block</lineMarker>()
}

suspend fun foo() {}