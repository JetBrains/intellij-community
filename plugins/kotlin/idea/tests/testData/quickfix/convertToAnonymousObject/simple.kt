// "Convert to anonymous object" "true"
// K2_ERROR: INTERFACE_AS_FUNCTION
interface I {
    fun foo(): String
}

fun test() {
    val i = <caret>I { "" }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix