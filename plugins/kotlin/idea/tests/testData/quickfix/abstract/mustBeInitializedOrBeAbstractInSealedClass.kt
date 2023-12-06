// "Make 'i' 'abstract'" "true"
sealed class A() {
    fun <caret>i() : Int
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix