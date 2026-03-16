// WITH_COROUTINES
// IGNORE_K1
// PROBLEM: none
package test

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

val x = flowOf("1", null).<caret>filterNotNull()