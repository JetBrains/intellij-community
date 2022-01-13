// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'fn' is never used
fun test() {
    class Test {
        operator fun invoke(a: Int, fn: () -> Unit) {}
    }
    val test = Test()
    test.i<caret>nvoke(0) {

    }
}
