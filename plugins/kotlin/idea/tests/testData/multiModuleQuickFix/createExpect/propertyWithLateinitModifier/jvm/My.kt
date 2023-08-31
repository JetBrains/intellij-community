// "Create expected property in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: "The declaration has &#39;lateinit&#39; modifier"
// DISABLE-ERRORS
// IGNORE_K2

actual lateinit var <caret>s: String