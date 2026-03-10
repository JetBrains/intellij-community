// "Make 'i' not abstract" "true"
// K2_ERROR: Abstract property 'i' in non-abstract class 'A'.
class A {
    <caret>abstract var i = 0
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase