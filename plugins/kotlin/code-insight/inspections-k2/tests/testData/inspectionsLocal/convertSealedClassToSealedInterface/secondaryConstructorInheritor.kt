// FIX: Convert to sealed interface
sealed class Result<caret>
class Success(val data: String) : Result() {
    constructor(data: String, source: String) : this("$source: $data")
}