// "Create expected function in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: Some types are not accessible from testModule_Common:,Some
// DISABLE-ERRORS
// IGNORE_K2
interface Some

actual fun <T : CommonClass, F : Some> <caret>foo(some: List<T>) {}