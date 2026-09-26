// FIX: Convert to sealed class
sealed interface Result<out T : Any><caret>
class Success<T : Any>(val result: T) : Result<T>
class Error(val throwable: Throwable) : Result<Nothing>