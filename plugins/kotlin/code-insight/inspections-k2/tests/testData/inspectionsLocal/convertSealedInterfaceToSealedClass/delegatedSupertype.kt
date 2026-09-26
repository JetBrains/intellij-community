// PROBLEM: none
sealed interface Result<caret> {
    fun process()
}
class Impl : Result {
    override fun process() {}
}
class Delegated(impl: Impl) : Result by impl