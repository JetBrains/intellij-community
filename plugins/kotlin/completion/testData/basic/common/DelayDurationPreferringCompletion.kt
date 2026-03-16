import kotlin.time.Duration
import kotlinx.coroutines.delay

suspend fun test() {
    delay(<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"Duration", "tailText":" (kotlin.time)"}
// EXIST: { "lookupString":"Long", "tailText":" (kotlin)"}
