// "Change to 'var'" "true"
// K2_ERROR: INAPPLICABLE_LATEINIT_MODIFIER

class A() {
    <caret>lateinit val foo: String
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix