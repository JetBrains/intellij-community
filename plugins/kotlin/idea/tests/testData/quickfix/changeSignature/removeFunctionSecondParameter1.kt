// "Remove parameter 'y'" "true"
// DISABLE_ERRORS

fun foo(x: Int, y: Int) {
    foo(<caret>);
    foo(1);
    foo(1, 2);
    foo(2, 3, sdsd);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix$Companion$RemoveParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix