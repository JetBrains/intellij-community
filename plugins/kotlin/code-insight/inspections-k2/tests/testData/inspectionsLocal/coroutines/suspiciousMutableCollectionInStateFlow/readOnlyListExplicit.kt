// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

val state = MutableStateFlow<List<Int>>(<caret>emptyList())