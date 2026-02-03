// FIX: Use property access syntax
// WITH_STDLIB
fun Thread.foo() {
    setName<caret>("name")
}