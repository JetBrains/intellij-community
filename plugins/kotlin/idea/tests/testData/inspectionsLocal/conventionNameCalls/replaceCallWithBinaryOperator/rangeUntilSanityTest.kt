// FIX: Replace with '..<'
fun test() {
    class Test {
        operator fun rangeUntil(a: Int): Test = Test()
    }
    val test = Test()
    test.range<caret>Until(1)
}