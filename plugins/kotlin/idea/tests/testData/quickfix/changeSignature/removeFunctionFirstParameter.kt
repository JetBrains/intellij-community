// "Remove parameter 'x'" "true"
// DISABLE-ERRORS

fun foo(x: Int, y: Int) {
    foo(<caret>);
    foo(1);
    foo(1, 2);
    foo(2, 3, sdsd);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix$Companion$RemoveParameterFix