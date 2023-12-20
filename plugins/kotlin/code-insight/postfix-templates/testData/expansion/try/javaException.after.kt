import java.lang.NumberFormatException

fun test() {
    try {
        Integer.parseInt("5")
    } catch (e: NumberFormatException) {
        throw e
    }
}