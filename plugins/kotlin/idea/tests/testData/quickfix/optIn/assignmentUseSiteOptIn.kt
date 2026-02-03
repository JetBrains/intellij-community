// "Opt in for 'MyOptIn' on statement" "true"

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo(): Int = 42

fun main() {
    val x = <caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix