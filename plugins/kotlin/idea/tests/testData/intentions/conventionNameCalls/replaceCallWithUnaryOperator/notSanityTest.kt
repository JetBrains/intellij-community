// INTENTION_TEXT: Replace with '!' operator
fun test() {
    class Test {
        operator fun not(): Test = Test()
    }
    val test = Test()
    test.n<caret>ot()
}
