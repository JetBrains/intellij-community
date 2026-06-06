// "Convert to anonymous object" "true"
// K2_ERROR: Interface 'interface B<T, U> : Any' does not have constructors.
interface B<T, U> {
    fun bar(x: T): U
}

val b = <caret>B<Int, String> { "" }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix