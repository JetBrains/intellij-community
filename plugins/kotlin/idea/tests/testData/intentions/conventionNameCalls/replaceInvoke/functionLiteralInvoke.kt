// AFTER-WARNING: Parameter 'fn' is never used
fun test() {
    class Test {
        operator fun invoke(fn: () -> Unit) {}
    }
    val test = Test()
    test.i<caret>nvoke {

    }
}
