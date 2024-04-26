// WITH_STDLIB
fun checkValue(value: Int, predicate: Predicate?) {
    @Suppress("UNNECESSARY_SAFE_CALL")
    if (predicate != null && value?.matches(predicate) == true) {
        println("It matched!")
    }
}

fun interface Predicate {
    fun matches(value: Int): Boolean
}

fun main() {
    checkValue(123) { it == 123 }
}

fun Int.matches(predicate: Predicate): Boolean = predicate.matches(this)