// PROBLEM: none
class A {
    private val x = <caret> lazy { "hello" }

    fun test(flag: Boolean, y: Lazy<String>) {
        println((if (flag) x else y).value)
    }
}
