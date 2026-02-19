// PROBLEM: none
class A<K, F>
fun <K : Any, F : Any> A<K, F>.smth(k: K, f: () -> F) = Unit
typealias LambdaAlias = (Int) -> Int
val b = A<Int, LambdaAlias>()
fun test(i: Int): Int = 42
fun check() {
    b.smth(42) { ::tes<caret>t }
}