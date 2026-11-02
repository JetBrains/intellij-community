// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: none

data class Foo(val x: String, val y: Int)

fun test() {
    val <caret>(x, y) = Foo("", 0)
}
