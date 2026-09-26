// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    <caret>delay(100)
}
