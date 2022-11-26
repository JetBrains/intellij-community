// PROBLEM: none
class Foo(val a: String, val b: Int) {
    operator fun component1() = a
    operator fun component2() = b
}

fun bar(f: Foo) {
    val (<caret>b, a) = f
}