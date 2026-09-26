// WITH_COROUTINES
// PROBLEM: 'Deferred' result is unused
// FIX: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

fun CoroutineScope.test() {
    <caret>async { 42 }
}