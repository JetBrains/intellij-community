// IGNORE_FIR

fun test() {
    val vect = MyIterable<Int>()

    vect
        .filter { it != 2 }
        .forEach { it: Int ->
            it.toString()
        }
}

class MyIterable<T> {
    fun filter(function: (T) -> Boolean) = this
    fun forEach(action: (T) -> Unit) {
    }
}
