// FIX: Use property access syntax
// WITH_STDLIB

fun test(value: Int) {
    val j = J()
    with(j) {
        val a = {
            println("Hello")
            <caret>setX(value)
        }
    }
}