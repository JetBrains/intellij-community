// "Make 'i' 'abstract'" "true"
// K2_ERROR: Property must be initialized or be abstract.
open class A() {
    var <caret>i : Int
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp