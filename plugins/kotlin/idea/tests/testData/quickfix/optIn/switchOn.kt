// "Opt in for 'RequiresOptIn' in module 'light_idea_test_case'" "true"
// IGNORE_K2
// COMPILER_ARGUMENTS: -version
// COMPILER_ARGUMENTS_AFTER: -version -opt-in=kotlin.RequiresOptIn
// DISABLE-ERRORS
// WITH_STDLIB
// LANGUAGE_VERSION: 1.6

@RequiresOptIn<caret>
annotation class MyExperimentalAPI

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeModuleOptInFix