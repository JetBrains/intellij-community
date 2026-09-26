// FIX: Convert to sealed class
sealed interface Result<caret> {
    private fun helper() = println("private")
    fun process() = helper()
}
class Success : Result