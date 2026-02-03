// "Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// COMPILER_ARGUMENTS_AFTER: -opt-in=kotlin.RequiresOptIn -opt-in=test.MyExperimentalAPI
// DISABLE_ERRORS
// WITH_STDLIB

package test

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some {
    @MyExperimentalAPI
    fun foo() {}
}

class Bar {
    fun bar() {
        Some().foo<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix