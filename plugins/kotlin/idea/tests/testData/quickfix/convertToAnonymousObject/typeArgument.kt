// "Convert to anonymous object" "true"
interface B<T, U> {
    fun bar(x: T): U
}

val b = <caret>B<Int, String> { "" }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix