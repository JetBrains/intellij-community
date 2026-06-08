
// FIX: Remove unreachable code
fun f(): Any? {
    <caret>return try {
        return null
    }
    catch (_: Throwable) {
        return null
    }
}
