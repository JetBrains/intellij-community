// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope


fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    Main.javaStaticWrapper {
        <caret>doStuff()
    }
}