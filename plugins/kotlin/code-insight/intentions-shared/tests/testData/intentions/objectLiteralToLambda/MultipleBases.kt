// IS_APPLICABLE: false
// WITH_STDLIB

interface I

fun foo(runnable: Runnable) {}

fun bar() {
    foo(<caret>object : Runnable, I {
        override fun run() {
        }
    })
}