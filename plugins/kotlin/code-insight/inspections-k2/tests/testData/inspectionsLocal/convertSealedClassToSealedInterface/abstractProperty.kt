// FIX: Convert to sealed interface
sealed class Result<caret> {
    abstract fun process()
    abstract val value: String
}