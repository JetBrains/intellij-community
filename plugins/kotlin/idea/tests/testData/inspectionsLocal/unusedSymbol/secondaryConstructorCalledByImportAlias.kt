// PROBLEM: none
import Foo as Alias

data class Foo(val x: Int) {
    <caret>constructor(x: Int, y: Int) : this(x + y)
}

fun main() {
    Alias(1, 2)
}