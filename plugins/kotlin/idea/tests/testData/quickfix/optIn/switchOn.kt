// "Opt in for 'RequiresOptIn' in module 'light_idea_test_case'" "true"
// COMPILER_ARGUMENTS: -version
// COMPILER_ARGUMENTS_AFTER: -version -opt-in=kotlin.RequiresOptIn
// DISABLE_ERRORS
// WITH_STDLIB
// LANGUAGE_VERSION: 1.6

@RequiresOptIn<caret>
annotation class MyExperimentalAPI

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix