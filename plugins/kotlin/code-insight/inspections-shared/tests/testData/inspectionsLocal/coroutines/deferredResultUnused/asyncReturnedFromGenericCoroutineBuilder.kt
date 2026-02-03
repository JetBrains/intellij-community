// WITH_COROUTINES
// PROBLEM: 'Deferred' result is unused
// FIX: none
package test

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

fun testAsyncInGenericBuilder() {
    <caret>runBlocking {
        async { 42 }
    }
}