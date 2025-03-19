// WITH_STDLIB
fun main() {
    var result: Int
    try {
        result = <warning descr="[DIVISION_BY_ZERO] Division by zero.">5 / 0</warning>
    } catch (e: ArithmeticException) {
        println("catched")
    }
}