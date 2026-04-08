// FIX: Convert to sealed interface
sealed class Result<caret> {
    var value: String
        get() = "default"
        set(v) { println(v) }
}
class Success : Result()