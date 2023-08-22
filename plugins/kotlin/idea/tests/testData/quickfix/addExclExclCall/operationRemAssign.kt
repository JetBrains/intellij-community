// "Add non-null asserted (!!) call" "true"
fun test() {
    var i: Int? = 0
    i %=<caret> 2
}

/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix