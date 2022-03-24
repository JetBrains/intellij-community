// "Create expected property in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: "The declaration has &#39;const&#39; modifier"
// DISABLE-ERRORS

actual const val <caret>s: String = "Hello"