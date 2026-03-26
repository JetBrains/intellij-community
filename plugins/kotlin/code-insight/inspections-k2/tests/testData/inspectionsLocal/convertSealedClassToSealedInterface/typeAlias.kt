// FIX: Convert to sealed interface
sealed class Result<caret>
typealias Res = Result
class Success : Res()
class Error : Result()
object Loading : Res()