// PROBLEM: none
fun test() {
    suspend fun localFirst() {}

    <caret>suspend fun localSecond() {
        localFirst()
    }
}

