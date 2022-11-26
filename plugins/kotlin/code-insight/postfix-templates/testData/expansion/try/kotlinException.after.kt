import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

fun test() {
    try {
        call()
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    }
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call() {}