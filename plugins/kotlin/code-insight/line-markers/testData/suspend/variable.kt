suspend fun test() {
    <lineMarker text="Suspend operator call &apos;invoke()&apos;">foo</lineMarker>()
}

val foo: suspend (String) -> String = { it }