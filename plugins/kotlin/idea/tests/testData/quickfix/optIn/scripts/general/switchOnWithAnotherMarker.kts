// "Add '-opt-in=kotlin.RequiresOptIn' to module light_idea_test_case compiler arguments" "true"
// COMPILER_ARGUMENTS: -XXLanguage:-OptInRelease -opt-in=AnotherMarker
// COMPILER_ARGUMENTS_AFTER: -XXLanguage:-OptInRelease -opt-in=AnotherMarker -opt-in=kotlin.RequiresOptIn
// DISABLE-ERRORS
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn<caret>
annotation class MyExperimentalAPI

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeModuleOptInFix