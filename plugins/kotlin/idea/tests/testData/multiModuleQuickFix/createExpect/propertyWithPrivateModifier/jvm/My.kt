// "Create expected property in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: "The declaration has &#39;private&#39; modifier"
// DISABLE-ERRORS

private actual val <caret>s: String = "s"