// WITH_STDLIB
fun checkValue(value: Int, predicate: Predicate?) {
    if (predicate != null && value<warning descr="[UNNECESSARY_SAFE_CALL] Unnecessary safe call on a non-null receiver of type Int">?.</warning>matches(predicate) == true) {
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