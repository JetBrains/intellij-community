// "Remove parameter 'y'" "true"
// DISABLE-ERRORS

fun foo(x: Int, y: Int) {
    foo();
    foo(1<caret>);
    foo(1, 2);
    foo(2, 3, sdsd);
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix$Companion$RemoveParameterFix