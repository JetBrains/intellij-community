import java.lang.Integer.parseInt
import java.lang.NumberFormatException

fun test() {
    try {
        parseInt("5")
    } catch (e: NumberFormatException) {
        throw e
    }
}