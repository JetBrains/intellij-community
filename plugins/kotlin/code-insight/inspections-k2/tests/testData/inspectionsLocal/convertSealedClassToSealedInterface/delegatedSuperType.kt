// PROBLEM: none
interface Delegate {
    fun doWork()
}
class DelegateImpl : Delegate {
    override fun doWork() {}
}
sealed class <caret>Result(delegate: Delegate) : Delegate by delegate