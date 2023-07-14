// "Add non-null asserted (!!) call" "true"
fun <T: Collection<Int>?> foo(c: T) {
    for (i in <caret>c) { }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix