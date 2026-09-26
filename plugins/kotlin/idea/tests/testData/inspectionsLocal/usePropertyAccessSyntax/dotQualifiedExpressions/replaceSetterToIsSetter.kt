// WITH_STDLIB
// FIX: Use property access syntax
fun foo(thread: Thread) {
    thread.<caret>setDaemon(true)
}