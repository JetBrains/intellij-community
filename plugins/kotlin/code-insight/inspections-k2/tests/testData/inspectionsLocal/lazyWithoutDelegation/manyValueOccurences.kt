class A {

    private val x =<caret> lazy { "hello" }

    fun test() {
        val len = x.value.length
        println(x.value)
    }
}