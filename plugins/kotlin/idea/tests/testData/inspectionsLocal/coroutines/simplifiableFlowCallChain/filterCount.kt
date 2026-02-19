// WITH_COROUTINES
// IGNORE_K1
package test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter

suspend fun test(flow: Flow<Int>) {
    flow.<caret>filter { it != 0 }.count()
}