// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(thread: Thread) {
    val x = thread.setName("name")<caret>
}