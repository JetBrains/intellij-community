// WITH_RUNTIME
// AFTER-WARNING: Parameter 'runnable' is never used

fun foo(runnable: Runnable) {}

fun bar() {
    foo(<caret>object : Runnable {
        override fun run() {
            throw UnsupportedOperationException()
        }
    })
}