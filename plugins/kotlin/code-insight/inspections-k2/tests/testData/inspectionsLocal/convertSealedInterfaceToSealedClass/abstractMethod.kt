// FIX: Convert to sealed class
sealed interface Result<caret> {
    fun process()
}
class Success : Result {
    override fun process() {}
}