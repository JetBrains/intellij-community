// FIX: Convert to sealed class
sealed interface Result<caret>
typealias Res = Result
class Success : Res
class Error : Result
object Loading : Res