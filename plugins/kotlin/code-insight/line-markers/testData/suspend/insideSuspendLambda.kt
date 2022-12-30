suspend fun test() {
    coroutine {
        <lineMarker text="Suspend function call &apos;foo()&apos;">foo</lineMarker>()
    }
}

fun coroutine(block: suspend () -> Unit) {
    block()
}

suspend fun foo() {}