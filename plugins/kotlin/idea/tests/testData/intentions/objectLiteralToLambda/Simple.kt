// WITH_RUNTIME

fun foo(runnable: Runnable) {}

fun bar() {
    foo(<caret>object : Runnable {
        override fun run() {
            throw UnsupportedOperationException()
        }
    })
}