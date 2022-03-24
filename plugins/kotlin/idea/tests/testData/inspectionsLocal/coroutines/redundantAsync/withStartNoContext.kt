// PROBLEM: none
// WITH_STDLIB

package kotlinx.coroutines

suspend fun test(ctx: CoroutineContext) {
    coroutineScope {
        <caret>async(start = CoroutineStart.LAZY) { 42 }.await()
    }
}