// AFTER-WARNING: Variable 'myFoo' is never used
// FIX: Use property access syntax
// WITH_STDLIB
fun foo() {
    val myFoo = Foo().apply {
        <caret>setFirst(10)
    }
}