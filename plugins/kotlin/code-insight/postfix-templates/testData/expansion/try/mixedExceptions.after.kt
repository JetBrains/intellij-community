import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Integer.parseInt
import java.lang.NumberFormatException

fun test() {
    try {
        call(1, 2, parseInt("5"))
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: NumberFormatException) {
        throw e
    }
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call(vararg x: Int) {}