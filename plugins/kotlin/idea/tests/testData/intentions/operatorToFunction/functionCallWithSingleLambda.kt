// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
// AFTER-WARNING: Variable 'testing' is never used
class Mocha() {
    operator fun invoke(f: (Int) -> String) {}
}
fun main() {
    val mocha = Mocha()
    val testing = mocha<caret>{ x: Int -> "hello world" }
}