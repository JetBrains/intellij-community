// FIX: Convert to sealed interface
sealed class Result<out T : Any><caret>()

class Success<T : Any>(val result: T) : Result<T>()
class Error : Result<Nothing>()