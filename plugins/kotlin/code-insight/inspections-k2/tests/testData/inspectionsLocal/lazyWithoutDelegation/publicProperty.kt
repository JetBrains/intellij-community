// PROBLEM: none
class A {
    val x =<caret> lazy { "hello" }

    fun test() {
        println(x.value)
    }
}