// "Remove 'protected' modifier" "true"
// K2_ERROR: INCOMPATIBLE_MODIFIERS
// K2_ERROR: INCOMPATIBLE_MODIFIERS

class A private <caret>protected constructor() {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase