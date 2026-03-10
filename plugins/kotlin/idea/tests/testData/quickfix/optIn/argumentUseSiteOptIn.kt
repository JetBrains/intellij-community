// "Opt in for 'MyOptIn' on statement" "true"
// WITH_STDLIB
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo(): String = "Hello"

fun main() {
    println(<caret>foo())
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix