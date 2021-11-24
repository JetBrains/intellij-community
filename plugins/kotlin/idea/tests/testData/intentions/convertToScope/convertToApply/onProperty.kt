// WITH_STDLIB
// AFTER-WARNING: Variable 'a' is never used

class MyClass {
    fun foo1() = Unit
    fun foo2() = Unit
    fun foo3() = Unit

    fun foo4() {
        <caret>val a = MyClass()
        a.foo1()
        a.foo2()
        a.foo3()
    }
}