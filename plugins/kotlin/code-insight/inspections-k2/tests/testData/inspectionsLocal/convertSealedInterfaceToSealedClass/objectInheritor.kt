// FIX: Convert to sealed class
sealed interface Result<caret>
object Loading : Result
data object Empty : Result
class Success : Result