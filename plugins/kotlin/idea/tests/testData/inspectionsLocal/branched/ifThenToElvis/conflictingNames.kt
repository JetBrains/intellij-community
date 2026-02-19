// WITH_STDLIB
sealed interface Suggestion {
    object YES : Suggestion
    object NO : Suggestion
}

fun value(value: Boolean?) = i<caret>f (value == null) Suggestion.NO else value(value)
fun value(value: Boolean) = if (value) Suggestion.YES else Suggestion.NO