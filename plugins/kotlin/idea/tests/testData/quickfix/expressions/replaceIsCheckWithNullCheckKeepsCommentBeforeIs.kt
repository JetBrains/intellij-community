// "Replace 'is' check with null check" "true"
// ERROR: IMPOSSIBLE_IS_CHECK_RELYING_ON_NULL_ERROR
class A
class B

fun test(a: A?) = a /* keep me */ <caret>is B?

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceIsCheckWithNullCheckFix
