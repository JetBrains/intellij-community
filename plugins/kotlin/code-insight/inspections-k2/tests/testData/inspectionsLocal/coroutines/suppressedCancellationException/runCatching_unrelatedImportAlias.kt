// WITH_COROUTINES
// PROBLEM: none
package test

import kotlin.run as tryIt
import kotlinx.coroutines.delay

suspend fun compute(): Int {
    return <caret>tryIt {
        delay(100)
        42
    }
}
