suspend fun test() {
    <lineMarker text="Suspend operator call 'invoke()'">foo</lineMarker>("foo")
}

val foo: suspend (String) -> String = { it }