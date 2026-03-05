// FIX: Convert to sealed class
sealed interface Result<caret> {
    var value: String
        get() = "default"
        set(v) { println(v) }
}
class Success : Result