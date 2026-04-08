// FIX: Convert to sealed interface
sealed class Result<caret>
class Failure : Result {
    constructor(message: String) : super()
    constructor(code: Int) : super()
}