// WITH_STDLIB
fun foo(<caret>p: Any) {
    object : Runnable {
        override fun run() {
            print(this)
        }
    }
}