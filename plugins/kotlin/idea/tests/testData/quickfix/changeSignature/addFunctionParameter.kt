// "Add parameter to function 'foo'" "true"
// DISABLE-ERRORS

fun foo(x: Int) {
    foo();
    foo(1);
    foo(1, 4<caret>);
    foo(2, 3, sdsd);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$applicator$1