// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
fun test() {
    class Test {
        operator fun invoke(a: Int, b: String) {}
    }
    val test = Test()
    test.i<caret>nvoke(b = "s", a = 1)
}
