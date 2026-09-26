// FIX: Convert to sealed class
sealed interface Result<caret> {
    fun process() = println("default")
}
class Success : Result {
    override fun process() = println("success")
}