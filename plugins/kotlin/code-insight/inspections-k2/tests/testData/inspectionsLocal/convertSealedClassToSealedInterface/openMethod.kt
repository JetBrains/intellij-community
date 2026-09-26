// FIX: Convert to sealed interface
sealed class Result<caret> {
    open fun process() = println("default")
}
class Success : Result() {
    override fun process() = println("success")
}