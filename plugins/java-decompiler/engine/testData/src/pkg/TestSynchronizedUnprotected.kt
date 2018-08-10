package pkg

class TestSynchronizedUnprotected {
    fun test() {
        synchronized(this) {
            println("Boom")
        }
    }
}