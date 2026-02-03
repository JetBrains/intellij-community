// WITH_COROUTINES
// PROBLEM: none
package test

import kotlin.coroutines.coroutineContext<caret> as ctx

suspend fun test() {
    ctx
}