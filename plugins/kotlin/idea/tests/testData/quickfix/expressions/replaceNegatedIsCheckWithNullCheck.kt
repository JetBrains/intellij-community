// "Replace 'is' check with null check" "true"
class A
class B

fun test(a: A?) {
    if (<caret>a !is B?) {
    }
}

// K2_ERROR: IMPOSSIBLE_IS_CHECK_RELYING_ON_NULL_ERROR
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceIsCheckWithNullCheckFix
