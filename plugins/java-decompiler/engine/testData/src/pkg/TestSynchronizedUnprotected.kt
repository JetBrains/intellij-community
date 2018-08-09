
object TestSynchronizedUnprotected {
    fun test() {
        synchronized(this) {
            println("Boom")
        }
    }
}