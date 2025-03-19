// "Create expected function in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: Some types are not accessible from testModule_Common:,SomeString
// DISABLE_ERRORS
// IGNORE_K2

typealias SomeString = String

actual fun <caret>foo(): SomeString = ""