// FIX: Convert to sealed class
sealed interface Result<caret> {
    private inline fun <reified T> cast(value: Any): T = value as T
}
class Success : Result