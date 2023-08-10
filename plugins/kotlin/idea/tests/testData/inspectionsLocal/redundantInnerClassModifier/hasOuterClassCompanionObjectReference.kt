class Test {
    <caret>inner class InnerM {
        val v = Test
        val foo = Test.foo
    }
    companion object {
        const val foo = 1
    }
}