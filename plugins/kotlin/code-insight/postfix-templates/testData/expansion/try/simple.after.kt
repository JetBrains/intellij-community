import java.lang.Exception

fun test() {
    try {
        "foo"
    } catch (e: Exception) {
        throw e
    }
}