// PROBLEM: none
// LANGUAGE_VERSION: 2.2
// K2_ERROR: UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL
// K2_ERROR: UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL
// K2_ERROR: UNSUPPORTED_FEATURE

context(i: Int) fun usesInt() {}

fun test() {
    <caret>context("", 42, 1.0) {
        usesInt()
    }
}