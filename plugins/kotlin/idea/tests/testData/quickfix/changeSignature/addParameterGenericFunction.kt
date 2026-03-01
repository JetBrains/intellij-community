// "Change the signature of function 'delegate'" "true"
// K2_AFTER_ERROR: Cannot infer type for type parameter 'S'. Specify it explicitly.
// K2_AFTER_ERROR: Unresolved reference 'T'.
fun <T> call(subject: T) = delegate(subject, <caret>1)

fun <S> delegate(subject: T) = subject

// IGNORE_K1

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix