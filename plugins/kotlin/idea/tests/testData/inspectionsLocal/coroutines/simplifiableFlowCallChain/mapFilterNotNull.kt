// WITH_COROUTINES
// IGNORE_K1
package test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

fun test(flow: Flow<Int>) {
    flow.<caret>map { if (it != 0) it else null }.filterNotNull()
}