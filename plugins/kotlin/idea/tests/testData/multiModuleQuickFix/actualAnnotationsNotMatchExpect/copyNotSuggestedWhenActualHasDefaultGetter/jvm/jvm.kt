// "Copy mismatched annotation 'Ann' from expect to actual declaration (may change semantics)" "false"
// IGNORE_IRRELEVANT_ACTIONS
// DISABLE-ERRORS
// FIR_COMPARISON

// Not supported scenario because of absent getter
actual val fo<caret>o: Any? = null
