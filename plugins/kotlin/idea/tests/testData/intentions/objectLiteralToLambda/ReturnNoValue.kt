// WITH_STDLIB
// AFTER-WARNING: Parameter 'runnable' is never used

fun foo(runnable: Runnable) {}

fun bar(p: Int) {
    foo(<caret>object : Runnable {
        override fun run() {
            if (p < 0) return
            println("a")
            println("b")
        }
    })
}