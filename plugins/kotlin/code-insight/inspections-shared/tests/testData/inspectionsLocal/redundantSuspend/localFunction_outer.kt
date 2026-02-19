suspend<caret> fun test(action: suspend () -> Unit) {
    suspend fun localSecond() {
        action()
    }
}

