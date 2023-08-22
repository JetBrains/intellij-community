// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(runnable: Runnable) {}

fun bar() {
    foo(<caret>object : Runnable {
        val v = "a".hashCode()

        override fun run() {
            print(v)
        }
    })
}