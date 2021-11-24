// WITH_STDLIB

package kotlinx.coroutines

suspend fun test(ctx: CoroutineContext) {
    GlobalScope.<caret>async(ctx) { 42 }.await()
}