// WITH_STDLIB
// AFTER-WARNING: Name shadowed: a
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Variable 'a' is never used
class MyClass {
    fun foo1() = Unit
    fun foo2() = Unit
    fun foo3() = Unit

    fun foo4(a: MyClass) {
        val a = MyClass()
        a.foo1()
        a.foo2()<caret>
        a.foo3()
    }
}