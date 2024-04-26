// "Add '-opt-in=test.MyExperimentalAPI' to module light_idea_test_case compiler arguments" "true"
// IGNORE_K2
// PRIORITY: LOW
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// COMPILER_ARGUMENTS_AFTER: -opt-in=kotlin.RequiresOptIn -opt-in=test.MyExperimentalAPI
// DISABLE-ERRORS
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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFileLevelFixesFactory$LowPriorityMakeModuleOptInFix