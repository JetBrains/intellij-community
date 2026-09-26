class Test {
    <caret>inner class InnerM {
        val o = O
        val foo = O.foo
    }
    object O {
        const val foo = 1
    }
}