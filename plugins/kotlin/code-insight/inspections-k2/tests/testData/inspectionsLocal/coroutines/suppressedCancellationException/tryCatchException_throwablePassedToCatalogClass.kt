// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

class Catalog {
    fun process(t: Throwable) {
        throw t
    }
}

private val CATALOG = Catalog()

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        CATALOG.process(e)
    }
}
