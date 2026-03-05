// FIX: Convert to sealed class
sealed interface <caret>Result
class Success(val data: String) : Result {
    constructor(data: String, source: String) : this("$source: $data")
}