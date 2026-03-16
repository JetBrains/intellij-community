// "Opt in for 'MyOptIn' on statement" "true"
// WITH_STDLIB

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo(): String = "Hello"

fun main() {
    println(<caret>foo())
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix