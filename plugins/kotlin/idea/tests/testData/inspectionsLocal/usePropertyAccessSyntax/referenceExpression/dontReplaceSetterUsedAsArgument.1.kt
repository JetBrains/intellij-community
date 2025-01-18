// PROBLEM: none
// K2-ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2-ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2-ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2-ERROR: Unresolved reference 'Foo'.
// K2-ERROR: Unresolved reference 'setFoo'.
fun test(a: Int) {}

fun main() {
    val foo = Foo()
    with(foo) {
        test(<caret>setFoo(5))
    }
}