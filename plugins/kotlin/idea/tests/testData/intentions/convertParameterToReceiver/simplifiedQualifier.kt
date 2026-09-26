// WITH_STDLIB
class A {
    fun foo3() = 42

    fun test(<caret>a: String) {
        foo3()
    }
}