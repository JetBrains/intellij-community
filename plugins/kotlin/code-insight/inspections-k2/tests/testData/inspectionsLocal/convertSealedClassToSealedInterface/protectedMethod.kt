// FIX: Convert to sealed interface
sealed class Result<caret> {
    protected open fun process() = println("protected")
}
class Success : Result()