fun test() {
    try {
        call()
    } catch (e: java.lang.IllegalStateException) {
        throw e
    } catch (e: java.lang.IllegalArgumentException) {
        throw e
    }
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call() {}