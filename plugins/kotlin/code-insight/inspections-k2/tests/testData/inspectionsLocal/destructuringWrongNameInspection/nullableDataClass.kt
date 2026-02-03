// PROBLEM: none
data class Foo(val a: String, val b: Int)
operator fun Foo?.component1() = this?.a ?: 0
operator fun Foo?.component2() = this?.b ?: 0
fun bar(f: Foo?) {
    val (x, <caret>a) = f
}