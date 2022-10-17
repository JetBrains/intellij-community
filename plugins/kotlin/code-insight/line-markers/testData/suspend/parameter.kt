suspend fun test(block: suspend () -> Unit) {
    block()
}