// "Remove redundant semicolon" "true"
fun foo() {
    a();<caret>
    b()
}

fun a(){}
fun b(){}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.DeletePsiElementOfInterestFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.DeletePsiElementOfInterestFix
