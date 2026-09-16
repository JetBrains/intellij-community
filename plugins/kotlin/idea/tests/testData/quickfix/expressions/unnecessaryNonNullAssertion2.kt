// "Remove unnecessary non-null assertion (!!)" "true"
fun test(value : String) {
    value!!<caret>.equals("test")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix