// FIR_COMPARISON
// CONFIGURE_LIBRARY: Coroutines
import kotlinx.coroutines.flow.flowOf

suspend fun main() {
    flowOf(1, 2, 3).coll<caret>
}

// WITH_ORDER
// EXIST: { itemText: "collect", tailText:"() for Flow<*> in kotlinx.coroutines.flow" }
// EXIST: { itemText: "collect", tailText:"(collector: FlowCollector<Int>)" }
