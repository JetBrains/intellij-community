// PROBLEM: none
// WITH_RUNTIME
fun test(f : MyFuture<Void>) = <caret>f.get()

interface MyFuture<T> {
    fun get():T?
}