// "Make 'T' reified and 'dereferenceClass' inline" "true"
// K2_ERROR: TYPE_PARAMETER_AS_REIFIED

fun <T: Any> dereferenceClass(): Any =
        T::class<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix