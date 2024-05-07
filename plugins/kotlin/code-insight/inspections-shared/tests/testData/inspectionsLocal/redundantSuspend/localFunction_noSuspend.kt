fun test() {
    fun localFirst() {}

    <caret>suspend fun localSecond() {
        localFirst()
    }
}

