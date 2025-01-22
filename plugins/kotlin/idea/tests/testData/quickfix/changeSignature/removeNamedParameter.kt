// "Remove parameter 'x'" "true"
// DISABLE_ERRORS

fun foo(x: Int, y: Int) {
    foo();
    foo(y = 1<caret>);
    foo(1, 2);
    foo(2, 3, sdsd);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix$Companion$RemoveParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix