import kotlin.time.Duration
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow

suspend fun test() {
    flow {}.debounce(<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"Duration", "tailText":" (kotlin.time)"}
// EXIST: { "lookupString":"Long", "tailText":" (kotlin)"}
