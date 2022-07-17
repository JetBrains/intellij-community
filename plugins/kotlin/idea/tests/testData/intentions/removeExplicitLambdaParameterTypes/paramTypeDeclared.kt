// AFTER-WARNING: Variable 'foo' is never used
fun main() {
    val foo: (Int) -> String = {x: Int<caret> -> "This number is " + x}
}
