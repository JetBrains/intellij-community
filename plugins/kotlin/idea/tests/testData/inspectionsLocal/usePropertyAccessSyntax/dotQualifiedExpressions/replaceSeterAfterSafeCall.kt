// FIX: Use property access syntax
// WITH_STDLIB
fun foo(thread: Thread?) {
    thread?.setName<caret>("name")
}