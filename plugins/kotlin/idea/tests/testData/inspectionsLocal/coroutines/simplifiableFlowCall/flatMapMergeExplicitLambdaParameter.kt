// WITH_COROUTINES
// IGNORE_K1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge

fun test(flow: Flow<Flow<Int>>) {
    flow.<caret>flatMapMerge { f -> f }
}