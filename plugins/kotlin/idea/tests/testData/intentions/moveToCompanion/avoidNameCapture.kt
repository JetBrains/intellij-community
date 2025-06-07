// IGNORE_K1

class Test {
    val test1 = Test()

    fun foo(a: Test) {}

    fun test<caret>() {
        val test: Int = 5
        foo(test1)
        foo(this)
        class test2
    }
}