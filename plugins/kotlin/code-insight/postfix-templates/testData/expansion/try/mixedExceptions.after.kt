import java.lang.NumberFormatException

fun test() {
    try {
        call(1, 2, Integer.parseInt("5"))
    } catch (e: java.lang.IllegalStateException) {
        throw e
    } catch (e: java.lang.IllegalArgumentException) {
        throw e
    } catch (e: NumberFormatException) {
        throw e
    }
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
fun call(vararg x: Int) {}