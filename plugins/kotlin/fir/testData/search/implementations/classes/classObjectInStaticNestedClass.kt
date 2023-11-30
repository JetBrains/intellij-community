interface TestInterface {
    fun t<caret>est()
}

class A {
    class B {
        companion object : TestInterface {
            override fun test() {
            }
        }
    }
}