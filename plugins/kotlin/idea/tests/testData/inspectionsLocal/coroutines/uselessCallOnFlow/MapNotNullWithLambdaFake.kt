// WITH_COROUTINES
// IGNORE_K1
// PROBLEM: none
package test

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull

val x = flowOf("1").<caret>mapNotNull { if (it.isNotEmpty()) it.toInt() else null }