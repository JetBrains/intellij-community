// WITH_COROUTINES
// IGNORE_K1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

fun test(flow: Flow<String?>) {
    flow.<caret>filter { it != null }
}