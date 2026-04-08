// FIX: Convert to sealed class
interface Base {
    fun process()
}
sealed interface Result<caret> : Base {
    override fun process() = println("override")
}
class Success : Result