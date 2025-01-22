// "Opt in for 'RequiresOptIn' in module 'light_idea_test_case'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:-OptInRelease
// COMPILER_ARGUMENTS_AFTER: -XXLanguage:-OptInRelease -opt-in=kotlin.RequiresOptIn
// DISABLE_ERRORS
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn<caret>
annotation class MyExperimentalAPI

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix