// "Add '-opt-in=kotlin.RequiresOptIn' to module light_idea_test_case compiler arguments" "true"
// COMPILER_ARGUMENTS: -version
// COMPILER_ARGUMENTS_AFTER: -version -opt-in=kotlin.RequiresOptIn
// DISABLE-ERRORS
// WITH_STDLIB
// LANGUAGE_VERSION: 1.6

@RequiresOptIn<caret>
annotation class MyExperimentalAPI
