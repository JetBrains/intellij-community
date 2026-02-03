// "Add non-null asserted (test!!) call" "true"
// K2_AFTER_ERROR: 'operator' modifier is required on 'fun iterator(): Iterator<Int>' defined in 'Some'.

class Some {
    fun iterator(): Iterator<Int> = null!!
}

fun foo() {
    val test: Some? = Some()
    for (i in <caret>test) { }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix