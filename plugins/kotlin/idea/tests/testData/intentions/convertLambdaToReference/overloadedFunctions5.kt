// AFTER-WARNING: Parameter 'bound' is never used
// AFTER-WARNING: Variable 'random' is never used
fun nextInt(): Int {
    return 42
}

fun nextInt(bound: Int): Int {
    return 42
}

fun foo(f: (Int) -> Int) = f(10)

fun main() {
    val random = foo <caret>{ nextInt(it) }
}