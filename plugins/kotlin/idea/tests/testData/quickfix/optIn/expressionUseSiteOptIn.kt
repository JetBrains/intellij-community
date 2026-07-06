// "Opt in for 'MyOptIn' on statement" "true"
// K2_ERROR: OPT_IN_USAGE_ERROR

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo() {}

fun main() {
    <caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix