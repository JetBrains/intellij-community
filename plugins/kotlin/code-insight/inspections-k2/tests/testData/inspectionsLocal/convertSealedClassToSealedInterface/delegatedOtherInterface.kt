// FIX: Convert to sealed interface
interface Other {
    fun other()
}
class OtherImpl : Other {
    override fun other() {}
}
sealed class Result<caret>
class Success(other: OtherImpl) : Result(), Other by other