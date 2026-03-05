// FIX: Convert to sealed interface
sealed class Result<caret>
object Loading : Result()
data object Empty : Result()
class Success : Result()