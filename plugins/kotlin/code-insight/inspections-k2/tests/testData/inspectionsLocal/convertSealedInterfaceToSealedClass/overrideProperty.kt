// FIX: Convert to sealed class
interface Base {
    val value: String
}
sealed interface Result<caret> : Base {
    override val value: String get() = "default"
}
class Success : Result