// "Add parameter to function 'foo'" "true"
// DISABLE_ERRORS

fun foo(x: Int) {
    foo(,);
    foo(1);
    foo(2, java.util.LinkedHashSet<Int>()<caret>);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix