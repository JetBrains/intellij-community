// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
fun bar(i: Int) {}

fun main(param: Int) {
    param

    try {
        bar(param)
        val localVariable = param
        bar(localVariable)

        for (number in 1..10) {
            bar(number)
        }
    } catch (e: Throwable) {
        val msg = e.message
    }
}