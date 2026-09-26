// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.flow.MutableSharedFlow

val state = MutableSharedFlow<MutableList<Int>>(replay = <caret>1)