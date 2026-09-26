// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'runnable' is never used

fun foo(f: () -> Unit, runnable: Runnable) {}

fun bar() {
    foo({}, <caret>object : Runnable {
        override fun run() {
            throw UnsupportedOperationException()
        }
    })
}