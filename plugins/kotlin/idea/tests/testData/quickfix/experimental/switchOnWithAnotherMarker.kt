// "Add '-opt-in=kotlin.RequiresOptIn' to module light_idea_test_case compiler arguments" "true"
// COMPILER_ARGUMENTS: -version -opt-in=AnotherMarker
// COMPILER_ARGUMENTS_AFTER: -version -opt-in=AnotherMarker -opt-in=kotlin.RequiresOptIn
// DISABLE-ERRORS
// WITH_RUNTIME

@RequiresOptIn<caret>
annotation class MyExperimentalAPI
