// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    <caret>try {
        delay(100)
    } finally {
        println("done")
    }
}
