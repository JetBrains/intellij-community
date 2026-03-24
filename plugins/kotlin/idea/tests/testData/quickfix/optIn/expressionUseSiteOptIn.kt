// "Opt in for 'MyOptIn' on statement" "true"
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo() {}

fun main() {
    <caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix