// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'fn' is never used
fun <T> doSomething(a: T) {}

fun test() {
    class Test {
        operator fun invoke(a: Int, vararg b: String, fn: () -> Unit): String = "test"
    }
    val test = Test()
    doSomething(test.i<caret>nvoke(1, "a", "b") { })
}
