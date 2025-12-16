// PROBLEM: none
// cannot be flagged, because it is passed as a `Lazy<T>` to the `takeLazy` function
class A {
    private val x =<caret> lazy { "hello" }

    fun takeLazy(lazyValue: Lazy<String>) {}

    fun test() {
        takeLazy(lazyValue = x)
    }
}
