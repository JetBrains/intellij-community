class A {
    fun test() {
        val x =<caret> lazy { "hello" }
        println(x.value)
    }
}