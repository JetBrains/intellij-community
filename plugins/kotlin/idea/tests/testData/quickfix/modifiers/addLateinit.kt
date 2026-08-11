// "Add 'lateinit' modifier" "true"
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT

class A {
    private var a: String<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix