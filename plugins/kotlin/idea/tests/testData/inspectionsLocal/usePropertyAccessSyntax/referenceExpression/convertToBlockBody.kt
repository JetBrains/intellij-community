// FIX: Use property access syntax
class A(): Foo() {

    fun call(): Unit = <caret>setFoo(1)
}