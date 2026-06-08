// WITH_COROUTINES

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat

fun test(flow: Flow<Flow<Int>>) {
    flow.<caret>flatMapConcat { f -> f }
}