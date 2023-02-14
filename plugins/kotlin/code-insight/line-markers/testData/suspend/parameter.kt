suspend fun test(block: suspend () -> Unit) {
    <lineMarker text="Suspend operator call 'invoke()'">block</lineMarker>()
}