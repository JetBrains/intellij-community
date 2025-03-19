// PROBLEM: none
// K2_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// K2_ERROR: Unresolved reference 'Foo'.
// K2_ERROR: Unresolved reference 'setFoo'.
fun test(a: Int) {}

fun main() {
    val foo = Foo()
    with(foo) {
        test(<caret>setFoo(5))
    }
}