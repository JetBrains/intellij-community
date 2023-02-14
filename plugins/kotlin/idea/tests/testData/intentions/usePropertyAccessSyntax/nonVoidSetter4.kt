// WITH_STDLIB
// AFTER-WARNING: Variable 'myFoo' is never used

fun foo() {
    val myFoo = Foo().apply {
        <caret>setFirst(10)
    }
}