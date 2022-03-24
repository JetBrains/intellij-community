// PROBLEM: none
fun foo(): Int?<caret> = doActionAndReturnInt(action = { return@foo it })

inline fun <T> doActionAndReturnInt(action: (Int?) -> T): T = action(42)
