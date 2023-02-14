// PROBLEM: none

interface Foo {
    fun check(): String = "OK"
}
abstract class Base {
    abstract fun check(): String
}
abstract class Derived : Base(), Foo // has fake 'check()'
abstract class Derived2 : Derived() // has fake 'check()'

class Derived3 : Derived2() {
    override<caret> fun check(): String {
        return super.check()
    }
}