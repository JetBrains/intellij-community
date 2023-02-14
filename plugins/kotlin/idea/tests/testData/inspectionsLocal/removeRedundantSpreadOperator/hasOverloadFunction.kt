// PROBLEM: none
fun execute(x: Int) = "foo"

fun execute(vararg xs: Int) = "bar"

fun main() {
    execute(<caret>*intArrayOf(5))
}