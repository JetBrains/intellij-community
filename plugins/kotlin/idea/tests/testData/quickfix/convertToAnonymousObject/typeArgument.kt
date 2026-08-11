// "Convert to anonymous object" "true"
// K2_ERROR: INTERFACE_AS_FUNCTION
interface B<T, U> {
    fun bar(x: T): U
}

val b = <caret>B<Int, String> { "" }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix