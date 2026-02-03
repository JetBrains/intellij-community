// IGNORE_K1

val test1 = Test()
fun foo(a: Test) {}

class Test {
    fun test<caret>() {
        val test: Int = 5
        foo(test1)
        foo(this)
        class test2
    }
}