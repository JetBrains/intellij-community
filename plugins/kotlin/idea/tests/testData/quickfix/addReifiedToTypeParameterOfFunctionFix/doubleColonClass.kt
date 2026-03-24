// "Make 'T' reified and 'dereferenceClass' inline" "true"
// K2_ERROR: Cannot use 'T' as reified type parameter. Use a class instead.

fun <T: Any> dereferenceClass(): Any =
        T::class<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix