// WITH_COROUTINES
// IGNORE_K1
// PROBLEM: none
package test

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf

val x = flowOf(true, "1").<caret>filterIsInstance<String>()