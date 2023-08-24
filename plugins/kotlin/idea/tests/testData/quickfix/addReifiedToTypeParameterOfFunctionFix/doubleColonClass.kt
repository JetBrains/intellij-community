// "Make 'T' reified and 'dereferenceClass' inline" "true"

fun <T: Any> dereferenceClass(): Any =
        T::class<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix