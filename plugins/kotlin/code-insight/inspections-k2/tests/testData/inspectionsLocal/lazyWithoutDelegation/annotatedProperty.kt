// PROBLEM: none
annotation class Ann

class A {
    @Ann
    private val x =<caret> lazy { "hello" }

    fun test() {
        println(x.value)
    }
}
