// "Use inherited visibility" "true"
interface ParseResult<out T> {
    public val success : Boolean
    public val value : T
}

class Success<T>(<caret>internal override val value : T) : ParseResult<T> {
    public override val success : Boolean = true
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityModifierFix