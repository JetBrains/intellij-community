// PROBLEM: none
// WITH_RUNTIME
fun test() {
    val d = dods {
        <caret>RuntimeException()
    }
}

fun dods(err: () -> RuntimeException): RuntimeException {
    return err().also { println(it) }
}