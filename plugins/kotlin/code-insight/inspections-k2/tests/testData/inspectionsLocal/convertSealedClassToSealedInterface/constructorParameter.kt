// PROBLEM: none
sealed class Result<out T : Any>(val long: Long)<caret>
class Success<T : Any>(val result: T, long: Long) : Result<T>(long)