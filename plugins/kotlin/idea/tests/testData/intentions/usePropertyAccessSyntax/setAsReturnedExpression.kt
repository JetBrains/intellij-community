// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(thread: Thread) {
    return thread.setName("name")<caret>
}