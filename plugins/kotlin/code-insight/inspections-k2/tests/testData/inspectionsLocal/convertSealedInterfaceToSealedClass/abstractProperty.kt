// FIX: Convert to sealed class
sealed interface Result<caret> {
    val value: String
}
class Success(override val value: String) : Result