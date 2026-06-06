// "Convert to anonymous object" "true"
// K2_ERROR: Interface 'interface B<X, Y, Z> : Any' does not have constructors.
interface B<X, Y, Z> {
    fun bar(a: Z, b: Y): X
}

val b = <caret>B { a: Int, b: Long -> "" }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix