// PROBLEM: none
// WITH_STDLIB

import kotlin.coroutines.coroutineContext

<caret>suspend fun test() {
    coroutineContext
}