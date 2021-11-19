// WITH_STDLIB

package kotlinx.coroutines

suspend fun test(ctx: CoroutineContext, scope: CoroutineScope) {
    scope.<caret>async(ctx) { 42 }.await()
}