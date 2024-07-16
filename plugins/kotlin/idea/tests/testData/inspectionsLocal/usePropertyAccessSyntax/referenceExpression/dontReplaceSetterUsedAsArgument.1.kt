// PROBLEM: none

fun test(a: Int) {}

fun main() {
    val foo = Foo()
    with(foo) {
        test(<caret>setFoo(5))
    }
}