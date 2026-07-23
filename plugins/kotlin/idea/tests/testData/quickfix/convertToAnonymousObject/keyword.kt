// "Convert to anonymous object" "true"
// K2_ERROR: INTERFACE_AS_FUNCTION

interface I {
    fun `when`(): String
}

fun test() {
    val i = I<caret> { "" }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix