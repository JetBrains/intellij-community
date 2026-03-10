// "Add non-null asserted (a!!) call" "true"
// ERROR: Type mismatch: inferred type is String? but String was expected
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Array<String?>?'.
// K2_ERROR: Return type mismatch: expected 'String', actual 'String?'.
// K2_AFTER_ERROR: Return type mismatch: expected 'String', actual 'String?'.
fun foo(a: Array<String?>?): String {
    return <caret>a[0]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix