// IS_APPLICABLE: false
// WITH_STDLIB

fun foo() {
    <caret>object : Runnable {
        override fun run() {
            hashCode()
        }
    }.run()
}