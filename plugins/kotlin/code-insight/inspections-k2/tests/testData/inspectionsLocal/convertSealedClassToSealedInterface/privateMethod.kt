// FIX: Convert to sealed interface
sealed class Result<caret> {
    private fun helper() = println("private")
}
class Success : Result()