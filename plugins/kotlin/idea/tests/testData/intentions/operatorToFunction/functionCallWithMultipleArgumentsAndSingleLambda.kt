// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Variable 'testing' is never used
class Mocha() {
    operator fun invoke(x: Int, y: String, f: (Int) -> String) {
    }
}
fun main() {
    val mocha = Mocha()
    val testing = mocha<caret>(1, "fire"){ x: Int -> "hello world" }
}