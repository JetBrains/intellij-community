
// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

class Foo {
    val someFlow: Flow<Int>

    init {
        someFlow = flowOf<caret>(1, 2, 3)
    }
}