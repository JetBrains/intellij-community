// AFTER-WARNING: Parameter 'bound' is never used
// AFTER-WARNING: Variable 'random' is never used
class Random {
    fun nextInt(): Int {
        return 42
    }

    fun nextInt(bound: Int): Int {
        return 42
    }
}

fun main() {
    val random = {<caret> i: Int -> Random().nextInt(i) }
}