// "Remove unnecessary non-null assertion (!!)" "true"
fun test(value : String) : Int {
    return value<caret>!!.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix