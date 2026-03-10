// "Make 'foo' not abstract" "true"
// K2_ERROR: Abstract function 'foo' in non-abstract class 'A'.
// K2_ERROR: Function 'foo' with a body cannot be abstract.
class A() {
    <caret>abstract fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase