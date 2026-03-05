// FIX: Convert to sealed interface
sealed class Result<caret> {
    abstract fun process()
}
class Success : Result() {
    override fun process() {}
}